package me.wolfii.allthelogs.data.store.write;

import me.wolfii.allthelogs.data.extract.LogContinuation;
import me.wolfii.allthelogs.data.store.SourceKind;
import org.duckdb.DuckDBConnection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Links the files picked for a metadata update to each other, by the rule in {@link LogContinuation}, before
 * their metadata is applied. A file that continues the previous day's picked file takes its version and
 * player, and its lines before its own first line with a place take the place of that file's last line. This
 * also covers a live session that ran past midnight, whose day-before file is never stored as its own log.
 */
final class PickedContinuations {
    private record Picked(String sourcePath, String entryPath, String folder, LocalDate date, LocalDateTime start,
                          LocalDateTime end, String version, String user, String lastPlace) {
        Picked inheriting(String version, String user, String lastPlace) {
            return new Picked(sourcePath, entryPath, folder, date, start, end, version, user, lastPlace);
        }
    }

    private final List<Picked> picked = new ArrayList<>();

    void add(PreparedLog log) {
        if (log.firstLineTime() == null || log.lastLineTime() == null) return;
        String lastPlace = null;
        List<String> places = log.entryServerOrWorlds();
        if (places != null && !places.isEmpty()) lastPlace = places.getLast();
        picked.add(new Picked(log.sourcePath(), log.entryPath(),
            LogContinuation.folder(log.sourceKind() == SourceKind.ARCHIVE, log.sourcePath(), log.entryPath()),
            log.date(), log.firstLineTime(), log.lastLineTime(), log.minecraftVersion(), log.minecraftUser(),
            lastPlace));
    }

    /**
     * Fills the queued {@code metadata_patch} rows of each continuation, and the version of its stored log.
     */
    void apply(DuckDBConnection connection) throws SQLException {
        Map<String, List<Picked>> byFolder = new HashMap<>();
        for (Picked file : picked) byFolder.computeIfAbsent(file.folder(), key -> new ArrayList<>()).add(file);
        try (PreparedStatement users = connection.prepareStatement("""
                 UPDATE metadata_patch SET minecraft_user = ?
                 WHERE source_path = ? AND entry_path = ? AND minecraft_user IS NULL""");
             PreparedStatement places = connection.prepareStatement("""
                 UPDATE metadata_patch SET server_or_world = ?
                 WHERE source_path = ? AND entry_path = ? AND server_or_world IS NULL AND seq < coalesce(
                     (SELECT min(seq) FROM metadata_patch
                      WHERE source_path = ? AND entry_path = ? AND server_or_world IS NOT NULL),
                     2147483647)""");
             PreparedStatement versions = connection.prepareStatement("""
                 UPDATE log_file SET minecraft_version = ?
                 WHERE source_path = ? AND entry_path = ? AND lower(minecraft_version) = 'unknown'""")) {
            for (List<Picked> folder : byFolder.values()) {
                folder.sort(Comparator.comparing(Picked::start));
                for (int index = 0; index < folder.size(); index++) {
                    Picked file = folder.get(index);
                    if (!LogContinuation.lacksStart(file.version(), file.user())) continue;
                    Picked previous = predecessor(folder, file);
                    if (previous == null) continue;
                    if (previous.user() != null) {
                        bind(users, previous.user(), file);
                        users.execute();
                    }
                    if (previous.lastPlace() != null) {
                        bind(places, previous.lastPlace(), file);
                        places.setString(4, file.sourcePath());
                        places.setString(5, file.entryPath());
                        places.execute();
                    }
                    if (!LogContinuation.isUnknown(previous.version())) {
                        bind(versions, previous.version(), file);
                        versions.execute();
                    }
                    folder.set(index, file.inheriting(previous.version(), previous.user(),
                        file.lastPlace() != null ? file.lastPlace() : previous.lastPlace()));
                }
            }
        }
    }

    private static void bind(PreparedStatement statement, String value, Picked file) throws SQLException {
        statement.setString(1, value);
        statement.setString(2, file.sourcePath());
        statement.setString(3, file.entryPath());
    }

    private static Picked predecessor(List<Picked> folder, Picked file) {
        Picked best = null;
        for (Picked candidate : folder) {
            if (candidate == file || !LogContinuation.hasStart(candidate.version(), candidate.user())) continue;
            if (!LogContinuation.continues(candidate.date(), candidate.end(), file.date(), file.start())) continue;
            if (best == null || candidate.end().isAfter(best.end())) best = candidate;
        }
        return best;
    }
}
