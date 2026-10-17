package me.wolfii.allthelogs.data.store.write;

import me.wolfii.allthelogs.data.extract.LogContinuation;
import me.wolfii.allthelogs.data.store.SourceKind;
import org.duckdb.DuckDBConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills in stored logs that continue a session across midnight, by the rule in {@link LogContinuation}.
 * A continuation takes its predecessor's version and player, and its chat before its first message with a
 * place takes the place of the predecessor's last message. Chat only happens inside a world or on a
 * server, so that last place is where the player still was at midnight. Logs are handled in time order, so
 * a session over several midnights passes its metadata on day by day. Only logs the current import wrote,
 * or that a metadata update import matched, are filled in; their predecessor may have been stored earlier.
 * Only empty values are filled.
 */
public final class ContinuationLinker {
    private ContinuationLinker() {
    }

    private record Log(long id, String folder, LocalDate date, LocalDateTime start, LocalDateTime end,
                       String version, String user) {
        Log with(String version, String user) {
            return new Log(id, folder, date, start, end, version, user);
        }
    }

    /**
     * @param touched ids of the logs to fill in
     * @return how many logs took metadata from their predecessor
     */
    public static int link(DuckDBConnection connection, Set<Long> touched) throws SQLException {
        if (touched.isEmpty()) return 0;
        Map<String, List<Log>> byFolder = new HashMap<>();
        for (Log log : load(connection)) byFolder.computeIfAbsent(log.folder(), key -> new ArrayList<>()).add(log);
        int linked = 0;
        try (Updates updates = new Updates(connection)) {
            for (List<Log> folder : byFolder.values()) {
                folder.sort(Comparator.comparing(Log::start).thenComparingLong(Log::id));
                for (int index = 0; index < folder.size(); index++) {
                    Log log = folder.get(index);
                    if (!touched.contains(log.id()) || !LogContinuation.lacksStart(log.version(), log.user())) continue;
                    Log previous = predecessor(folder, log);
                    if (previous == null) continue;
                    Log joined = inherit(log, previous, updates);
                    if (joined != null) {
                        folder.set(index, joined);
                        linked++;
                    }
                }
            }
        }
        return linked;
    }

    /**
     * The log in the same folder that {@code log} continues, the latest-ending one when several qualify.
     */
    private static Log predecessor(List<Log> folder, Log log) {
        Log best = null;
        for (Log candidate : folder) {
            if (candidate.id() == log.id() || !LogContinuation.hasStart(candidate.version(), candidate.user())) continue;
            if (!LogContinuation.continues(candidate.date(), candidate.end(), log.date(), log.start())) continue;
            if (best == null || candidate.end().isAfter(best.end())) best = candidate;
        }
        return best;
    }

    /**
     * @return the log with what it took over, or null when there was nothing to take
     */
    private static Log inherit(Log log, Log previous, Updates updates) throws SQLException {
        String version = log.version();
        String user = log.user();
        boolean changed = false;
        if (version == null && previous.version() != null) {
            version = previous.version();
            updates.version(log.id(), version);
            changed = true;
        }
        if (user == null && previous.user() != null) {
            user = previous.user();
            updates.user(log.id(), user);
            changed = true;
        }
        String place = updates.lastPlace(previous.id());
        if (place != null && updates.leadingPlace(log.id(), place) > 0) changed = true;
        return changed ? log.with(version, user) : null;
    }

    private static List<Log> load(DuckDBConnection connection) throws SQLException {
        List<Log> logs = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT id, source_kind, source_path, entry_path, log_date, start_time, end_time,
                        minecraft_version, minecraft_user
                 FROM log_file
                 WHERE source_kind <> '%s'""".formatted(SourceKind.SESSION.name()))) {
            while (result.next()) {
                boolean archive = SourceKind.ARCHIVE.name().equals(result.getString(2));
                logs.add(new Log(result.getLong(1),
                    LogContinuation.folder(archive, result.getString(3), result.getString(4)),
                    result.getDate(5).toLocalDate(),
                    result.getTimestamp(6).toLocalDateTime(),
                    result.getTimestamp(7).toLocalDateTime(),
                    result.getString(8),
                    result.getString(9)));
            }
        }
        return logs;
    }

    /**
     * Prepared statements reused for every continuation.
     */
    private static final class Updates implements AutoCloseable {
        private final PreparedStatement version;
        private final PreparedStatement fileUser;
        private final PreparedStatement entryUsers;
        private final PreparedStatement lastPlace;
        private final PreparedStatement leadingPlace;

        Updates(DuckDBConnection connection) throws SQLException {
            version = connection.prepareStatement("UPDATE log_file SET minecraft_version = ? WHERE id = ?");
            fileUser = connection.prepareStatement("UPDATE log_file SET minecraft_user = ? WHERE id = ?");
            entryUsers = connection.prepareStatement(
                "UPDATE chat_entry SET minecraft_user = ? WHERE file_id = ? AND minecraft_user IS NULL");
            lastPlace = connection.prepareStatement(
                "SELECT server_or_world FROM chat_entry WHERE file_id = ? ORDER BY line_index DESC LIMIT 1");
            leadingPlace = connection.prepareStatement("""
                UPDATE chat_entry SET server_or_world = ?
                WHERE file_id = ? AND server_or_world IS NULL AND line_index < coalesce(
                    (SELECT min(line_index) FROM chat_entry WHERE file_id = ? AND server_or_world IS NOT NULL),
                    2147483647)""");
        }

        void version(long id, String value) throws SQLException {
            version.setString(1, value);
            version.setLong(2, id);
            version.execute();
        }

        void user(long id, String value) throws SQLException {
            fileUser.setString(1, value);
            fileUser.setLong(2, id);
            fileUser.execute();
            entryUsers.setString(1, value);
            entryUsers.setLong(2, id);
            entryUsers.execute();
        }

        /**
         * Place of the last message of a log, or null when it has none.
         */
        String lastPlace(long id) throws SQLException {
            lastPlace.setLong(1, id);
            try (ResultSet result = lastPlace.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }

        /**
         * Gives the messages before a log's first message with a place the place {@code value}.
         */
        int leadingPlace(long id, String value) throws SQLException {
            leadingPlace.setString(1, value);
            leadingPlace.setLong(2, id);
            leadingPlace.setLong(3, id);
            return leadingPlace.executeUpdate();
        }

        @Override
        public void close() throws SQLException {
            version.close();
            fileUser.close();
            entryUsers.close();
            lastPlace.close();
            leadingPlace.close();
        }
    }
}
