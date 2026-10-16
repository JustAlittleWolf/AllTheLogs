package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.ChatLog;
import org.duckdb.DuckDBConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Joins logs that continue a game session across midnight. Minecraft starts a new log file at midnight, and
 * that file has no game start line, so on its own it has no version, no player, and no server or world until
 * the player joins something again.
 * <p>
 * A log without a start line continues the log from the same folder dated the day before whose last line is
 * at most {@link #MAX_GAP} before its first line. It takes that log's version and player, and its chat
 * before its own first join or leave line takes the place the previous log ended on. Logs are handled in
 * time order, so a session over several midnights passes its metadata along day by day. Only empty values
 * are filled, so running this again changes nothing.
 */
final class ContinuationLinker {
    /** Largest gap between the previous log's last line and the continuation's first line. */
    static final Duration MAX_GAP = Duration.ofMinutes(10);
    private static final Duration MAX_OVERLAP = Duration.ofMinutes(1);

    private ContinuationLinker() {
    }

    private record Log(long id, String folder, LocalDate date, LocalDateTime start, LocalDateTime end,
                       String version, String user, Boolean startup, String endPlace, LocalDateTime firstPlaceTime) {
        Log inheriting(String version, String user, String endPlace) {
            return new Log(id, folder, date, start, end, version, user, startup, endPlace, firstPlaceTime);
        }
    }

    /**
     * @return how many logs took metadata from their predecessor
     */
    static int link(DuckDBConnection connection) throws SQLException {
        Map<String, List<Log>> byFolder = new HashMap<>();
        for (Log log : load(connection)) byFolder.computeIfAbsent(log.folder(), key -> new ArrayList<>()).add(log);
        int linked = 0;
        try (Updates updates = new Updates(connection)) {
            for (List<Log> folder : byFolder.values()) {
                folder.sort(Comparator.comparing(Log::start).thenComparingLong(Log::id));
                for (int index = 0; index < folder.size(); index++) {
                    Log log = folder.get(index);
                    if (!Boolean.FALSE.equals(log.startup())) continue;
                    Log previous = predecessor(folder, log);
                    if (previous == null) continue;
                    Log joined = inherit(log, previous, updates);
                    if (joined != log) {
                        folder.set(index, joined);
                        linked++;
                    }
                }
            }
        }
        return linked;
    }

    /**
     * The log from the day before in the same folder that ends closest before {@code log} starts, within
     * {@link #MAX_GAP}.
     */
    private static Log predecessor(List<Log> folder, Log log) {
        Log best = null;
        for (Log candidate : folder) {
            if (candidate.id() == log.id() || !candidate.date().equals(log.date().minusDays(1))) continue;
            Duration gap = Duration.between(candidate.end(), log.start());
            if (gap.compareTo(MAX_GAP) > 0 || gap.compareTo(MAX_OVERLAP.negated()) < 0) continue;
            if (best == null || candidate.end().isAfter(best.end())) best = candidate;
        }
        return best;
    }

    private static Log inherit(Log log, Log previous, Updates updates) throws SQLException {
        String version = log.version();
        String user = log.user();
        String endPlace = log.endPlace();
        boolean changed = false;
        if (isUnknown(version) && !isUnknown(previous.version())) {
            version = previous.version();
            updates.version(log.id(), version);
            changed = true;
        }
        if (user == null && previous.user() != null) {
            user = previous.user();
            updates.user(log.id(), user);
            changed = true;
        }
        if (previous.endPlace() != null && !previous.endPlace().isEmpty()) {
            changed |= updates.leadingPlace(log.id(), previous.endPlace(), log.firstPlaceTime()) > 0;
        }
        if (endPlace == null && previous.endPlace() != null) {
            endPlace = previous.endPlace();
            updates.endPlace(log.id(), endPlace);
            changed = true;
        }
        return changed ? log.inheriting(version, user, endPlace) : log;
    }

    private static List<Log> load(DuckDBConnection connection) throws SQLException {
        List<Log> logs = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT id, source_kind, source_path, entry_path, log_date, start_time, end_time,
                        minecraft_version, minecraft_user, has_startup, end_place, first_place_time
                 FROM log_file
                 WHERE source_kind <> '%s'""".formatted(SourceKind.SESSION.name()))) {
            while (result.next()) {
                Timestamp firstPlace = result.getTimestamp(12);
                boolean hasStartup = result.getBoolean(10);
                Boolean startup = result.wasNull() ? null : hasStartup;
                logs.add(new Log(result.getLong(1),
                    folder(result.getString(2), result.getString(3), result.getString(4)),
                    result.getDate(5).toLocalDate(),
                    result.getTimestamp(6).toLocalDateTime(),
                    result.getTimestamp(7).toLocalDateTime(),
                    result.getString(8),
                    result.getString(9),
                    startup,
                    result.getString(11),
                    firstPlace == null ? null : firstPlace.toLocalDateTime()));
            }
        }
        return logs;
    }

    /**
     * The folder a log was found in: the file's directory, or the archive plus the directory inside it.
     */
    static String folder(String kind, String sourcePath, String entryPath) {
        if (SourceKind.ARCHIVE.name().equals(kind)) return sourcePath + "!" + parent(entryPath);
        return parent(sourcePath);
    }

    private static String parent(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static boolean isUnknown(String version) {
        return version == null || version.isBlank() || ChatLog.UNKNOWN_VERSION.equalsIgnoreCase(version);
    }

    /**
     * Prepared statements reused for every continuation.
     */
    private static final class Updates implements AutoCloseable {
        private final PreparedStatement version;
        private final PreparedStatement fileUser;
        private final PreparedStatement entryUsers;
        private final PreparedStatement places;
        private final PreparedStatement endPlace;

        Updates(DuckDBConnection connection) throws SQLException {
            version = connection.prepareStatement("UPDATE log_file SET minecraft_version = ? WHERE id = ?");
            fileUser = connection.prepareStatement("UPDATE log_file SET minecraft_user = ? WHERE id = ?");
            entryUsers = connection.prepareStatement(
                "UPDATE chat_entry SET minecraft_user = ? WHERE file_id = ? AND minecraft_user IS NULL");
            places = connection.prepareStatement("""
                UPDATE chat_entry SET server_or_world = ?
                WHERE file_id = ? AND server_or_world IS NULL AND (CAST(? AS TIMESTAMP) IS NULL OR entry_time < ?)""");
            endPlace = connection.prepareStatement("UPDATE log_file SET end_place = ? WHERE id = ?");
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
         * Gives chat before {@code firstPlaceTime}, or all chat when it is null, the place {@code value}.
         */
        int leadingPlace(long id, String value, LocalDateTime firstPlaceTime) throws SQLException {
            Timestamp limit = firstPlaceTime == null ? null : Timestamp.valueOf(firstPlaceTime);
            places.setString(1, value);
            places.setLong(2, id);
            places.setTimestamp(3, limit);
            places.setTimestamp(4, limit);
            return places.executeUpdate();
        }

        void endPlace(long id, String value) throws SQLException {
            endPlace.setString(1, value);
            endPlace.setLong(2, id);
            endPlace.execute();
        }

        @Override
        public void close() throws SQLException {
            version.close();
            fileUser.close();
            entryUsers.close();
            places.close();
            endPlace.close();
        }
    }
}
