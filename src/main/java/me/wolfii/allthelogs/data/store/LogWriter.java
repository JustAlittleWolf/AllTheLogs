package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.ImportOptions;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes parsed logs into the database on a single thread. DuckDB's appender is thread-confined, so import
 * parallelism lives entirely in the parsing stage that feeds this writer.
 */
public final class LogWriter implements AutoCloseable {
    private static final int FLUSH_INTERVAL = 100_000;
    /**
     * File-imported lines that repeat a line already stored before this import, with the same text this
     * close, are dropped. Repeats inside the import itself are kept. Log files write a linebreak as the
     * two characters {@code \n}; live capture stores a real newline.
     */
    static final int LIVE_DUPLICATE_WINDOW_SECONDS = EntryMatch.WINDOW_SECONDS;

    private final DuckDBConnection connection;
    private final DuckDBAppender fileAppender;
    private final DuckDBAppender entryAppender;
    private DuckDBAppender metadataAppender;
    private final Map<String, Long> existingLocations = new ConcurrentHashMap<>();
    private final Set<String> seenLocations = ConcurrentHashMap.newKeySet();
    private final Set<String> seenHashes = ConcurrentHashMap.newKeySet();
    private final Set<SeenLocation> pendingSeen = ConcurrentHashMap.newKeySet();
    private final Set<String> knownSessionIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> keepEvenIfEmpty = ConcurrentHashMap.newKeySet();
    /** First file id handed out by this writer; counters and dedup bookkeeping are scoped to this import. */
    private final long sessionStartId;
    private long nextFileId;
    private long bufferedEntries;
    private long writtenEntries;
    private int writtenFiles;
    private boolean metadataPatchReady;
    private int metadataPatchFiles;

    public LogWriter(DuckDBConnection connection) throws SQLException {
        this.connection = connection;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id, source_kind, source_path, entry_path FROM log_file")) {
            while (result.next()) {
                long id = result.getLong(1);
                String kind = result.getString(2);
                String sourcePath = result.getString(3);
                String entryPath = result.getString(4);
                existingLocations.put(locationKey(sourcePath, entryPath), id);
                if (SourceKind.SESSION.name().equals(kind)) {
                    String sessionId = SessionMarker.idFromEntryPath(entryPath);
                    if (sessionId != null) knownSessionIds.add(sessionId);
                }
                nextFileId = Math.max(nextFileId, id + 1);
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT source_path, entry_path, content_hash FROM import_seen")) {
            while (result.next()) {
                seenLocations.add(locationKey(result.getString(1), result.getString(2)));
                noteHash(result.getString(3));
            }
        }
        this.sessionStartId = nextFileId;
        // One transaction for the whole import: the appender would otherwise sync to disk every 204,800 rows.
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN TRANSACTION");
        }
        this.fileAppender = connection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "log_file");
        this.entryAppender = connection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "chat_entry");
    }

    private static String locationKey(String sourcePath, String entryPath) {
        return sourcePath + "\u0000" + entryPath;
    }

    private record SeenLocation(String sourcePath, String entryPath, String contentHash) {
    }

    /**
     * Whether a log at this location has already been stored or considered (and therefore skipped)
     * on an earlier import with {@code skipAlreadyImported}.
     */
    public boolean isAlreadyImported(String sourcePath, String entryPath) {
        String key = locationKey(sourcePath, entryPath);
        return existingLocations.containsKey(key) || seenLocations.contains(key);
    }

    /**
     * Whether a log with this SHA-256 of raw bytes has already been stored or considered.
     */
    public boolean hasContentHash(String contentHash) {
        return contentHash != null && !contentHash.isBlank() && seenHashes.contains(contentHash);
    }

    /**
     * Remembers a content hash for the rest of this import so a later copy in the same walk is skipped.
     */
    public void noteHash(String contentHash) {
        if (contentHash != null && !contentHash.isBlank()) {
            seenHashes.add(contentHash);
        }
    }

    /**
     * Remembers a log that was opened and then skipped, so a later {@code skipAlreadyImported} run
     * does not parse it again. Empty files, files with no timestamps, and live-session duplicates
     * are not stored as {@code log_file} rows. Identical copies are skipped by {@code contentHash}.
     */
    public void markConsidered(String sourcePath, String entryPath, String contentHash) {
        String key = locationKey(sourcePath, entryPath);
        seenLocations.add(key);
        noteHash(contentHash);
        pendingSeen.add(new SeenLocation(sourcePath, entryPath, contentHash));
    }

    /**
     * Whether a live capture session with this id is already stored. A log file that contains a
     * {@link SessionMarker} for such a session should be skipped.
     */
    public boolean hasSession(String sessionId) {
        return sessionId != null && knownSessionIds.contains(sessionId);
    }

    /**
     * Stores a parsed log, replacing any previously stored log at the same location.
     */
    public void write(PreparedLog log) throws SQLException {
        Long previous = existingLocations.get(locationKey(log.sourcePath(), log.entryPath()));
        if (previous != null) {
            // Replaced rows may still sit in the appender buffers.
            flushAppenders();
            deleteFile(previous);
        }

        long fileId = nextFileId++;
        List<LocalDateTime> times = log.entryTimes();
        List<String> messages = log.messages();
        List<long[]> formattings = log.formattings();
        List<String> users = log.entryUsers();
        List<String> places = log.entryServerOrWorlds();

        fileAppender.beginRow();
        fileAppender.append(fileId);
        fileAppender.append(log.fileName());
        fileAppender.append(log.sourceKind().name());
        fileAppender.append(log.sourcePath());
        fileAppender.append(log.entryPath());
        fileAppender.append(log.date());
        fileAppender.append(log.minecraftVersion());
        fileAppender.append(log.firstLineTime());
        fileAppender.append(log.lastLineTime());
        fileAppender.append((long) times.size());
        if (log.minecraftUser() == null) {
            fileAppender.appendNull();
        } else {
            fileAppender.append(log.minecraftUser());
        }
        fileAppender.endRow();

        for (int i = 0; i < times.size(); i++) {
            entryAppender.beginRow();
            entryAppender.append(fileId);
            entryAppender.append(i);
            entryAppender.append(times.get(i));
            entryAppender.append(messages.get(i));
            long[] formatting = formattings.get(i);
            if (formatting == null || formatting.length == 0) {
                entryAppender.appendNull();
            } else {
                entryAppender.append(formatting);
            }
            String user = users != null && i < users.size() ? users.get(i) : log.minecraftUser();
            if (user == null) {
                entryAppender.appendNull();
            } else {
                entryAppender.append(user);
            }
            String place = places != null && i < places.size() ? places.get(i) : null;
            if (place == null) {
                entryAppender.appendNull();
            } else {
                entryAppender.append(place);
            }
            entryAppender.endRow();
        }

        if (times.isEmpty() && log.resourceManagerReloaded()) keepEvenIfEmpty.add(fileId);
        existingLocations.put(locationKey(log.sourcePath(), log.entryPath()), fileId);
        markConsidered(log.sourcePath(), log.entryPath(), log.contentHash());
        writtenFiles++;
        writtenEntries += times.size();
        bufferedEntries += times.size();
        if (bufferedEntries >= FLUSH_INTERVAL) flushAppenders();
    }

    /**
     * Queues metadata from a parsed log for already stored chat lines. No new {@code log_file} or
     * {@code chat_entry} rows are inserted. Call {@link #applyMetadataPatches(ImportOptions)} once after
     * every file in the import has been queued so the store is not rewritten per file.
     * <p>
     * Matching prefers the same source path and line index, and otherwise the same message text within
     * {@link EntryMatch#WINDOW_SECONDS}. Formatting is written only when the stored line has none
     * and the log line has some. Username and server/world are written only when the log value is
     * non-null. Live session fields that already have a value are left unchanged.
     */
    public void updateMetadata(PreparedLog log, ImportOptions options) throws SQLException {
        markConsidered(log.sourcePath(), log.entryPath(), log.contentHash());
        if (!options.updatesAnyMetadata() || log.messages().isEmpty()) {
            return;
        }
        flushAppenders();
        ensureMetadataPatchTable();
        List<LocalDateTime> times = log.entryTimes();
        List<String> messages = log.messages();
        List<long[]> formattings = log.formattings();
        List<String> users = log.entryUsers();
        List<String> places = log.entryServerOrWorlds();
        for (int i = 0; i < times.size(); i++) {
            metadataAppender.beginRow();
            metadataAppender.append(log.sourcePath());
            metadataAppender.append(log.entryPath());
            metadataAppender.append(i);
            metadataAppender.append(times.get(i));
            metadataAppender.append(messages.get(i));
            long[] formatting = formattings != null && i < formattings.size() ? formattings.get(i) : null;
            if (formatting == null || formatting.length == 0) {
                metadataAppender.appendNull();
            } else {
                metadataAppender.append(formatting);
            }
            String user = users != null && i < users.size() ? users.get(i) : log.minecraftUser();
            if (user == null) {
                metadataAppender.appendNull();
            } else {
                metadataAppender.append(user);
            }
            String place = places != null && i < places.size() ? places.get(i) : null;
            if (place == null) {
                metadataAppender.appendNull();
            } else {
                metadataAppender.append(place);
            }
            metadataAppender.endRow();
        }
        metadataPatchFiles++;
    }

    /**
     * Applies every queued {@link #updateMetadata} row in one pass over {@code chat_entry}.
     * <p>
     * Path matches and near-in-time text matches are separate equijoins. An {@code OR} of those
     * predicates nested-loops the whole {@code chat_entry} table against every patch row, which is
     * what made metadata-only imports sit on "Optimizing database..." until they timed out.
     */
    public void applyMetadataPatches(ImportOptions options) throws SQLException {
        if (!metadataPatchReady || metadataPatchFiles == 0 || !options.updatesAnyMetadata()) {
            return;
        }
        flushMetadataAppender();
        String formattingFlag = options.updateFormatting() ? "true" : "false";
        String userFlag = options.updateMinecraftUser() ? "true" : "false";
        String serverFlag = options.updateMinecraftServer() ? "true" : "false";
        String session = SourceKind.SESSION.name();
        try (Statement update = connection.createStatement()) {
            prepareNormalizedPatches(update);
            int updated = update.executeUpdate("""
                UPDATE chat_entry
                SET
                    formatting = CASE
                        WHEN %s AND chat_entry.formatting IS NULL AND m.new_formatting IS NOT NULL
                        THEN m.new_formatting ELSE chat_entry.formatting END,
                    minecraft_user = CASE
                        WHEN %s AND m.new_user IS NOT NULL
                             AND (m.kind <> '%s' OR chat_entry.minecraft_user IS NULL)
                        THEN m.new_user ELSE chat_entry.minecraft_user END,
                    server_or_world = CASE
                        WHEN %s AND m.new_place IS NOT NULL
                             AND (m.kind <> '%s' OR chat_entry.server_or_world IS NULL)
                        THEN m.new_place ELSE chat_entry.server_or_world END
                FROM (
                    SELECT rid, kind, new_formatting, new_user, new_place FROM (
                        SELECT
                            rid,
                            kind,
                            new_formatting,
                            new_user,
                            new_place,
                            row_number() OVER (
                                PARTITION BY rid
                                ORDER BY pref, dt, seq
                            ) AS rn
                        FROM (
                            SELECT
                                e.rowid AS rid,
                                f.source_kind AS kind,
                                p.formatting AS new_formatting,
                                p.minecraft_user AS new_user,
                                p.server_or_world AS new_place,
                                0 AS pref,
                                abs(date_diff('millisecond', e.entry_time, p.entry_time)) AS dt,
                                p.seq
                            FROM metadata_patch_norm p
                            JOIN log_file f
                              ON f.source_path = p.source_path AND f.entry_path = p.entry_path
                            JOIN chat_entry e ON e.file_id = f.id AND e.line_index = p.seq
                            UNION ALL
                            SELECT
                                e.rowid,
                                f.source_kind,
                                p.formatting,
                                p.minecraft_user,
                                p.server_or_world,
                                1,
                                abs(date_diff('millisecond', e.entry_time, p.entry_time)),
                                p.seq
                            FROM metadata_patch_seconds p
                            JOIN chat_entry e
                              ON date_trunc('second', e.entry_time) = p.join_second
                             AND %s
                            JOIN log_file f ON f.id = e.file_id
                            WHERE %s
                        ) hits
                    ) ranked
                    WHERE rn = 1
                ) m
                WHERE chat_entry.rowid = m.rid
                """.formatted(formattingFlag, userFlag, session, serverFlag, session,
                    EntryMatch.sameAsNormalized("e.message", "p.text"),
                    EntryMatch.withinWindow("e.entry_time", "p.entry_time")));
            if (updated > 0 && options.updateMinecraftUser()) {
                updateLogFileUsers();
            }
            update.execute("DROP TABLE IF EXISTS metadata_patch_seconds");
            update.execute("DROP TABLE IF EXISTS metadata_patch_norm");
            update.execute("DROP TABLE IF EXISTS metadata_patch");
            if (updated > 0) {
                writtenFiles += metadataPatchFiles;
                writtenEntries += updated;
            }
        }
        metadataPatchReady = false;
        metadataPatchFiles = 0;
    }

    private void ensureMetadataPatchTable() throws SQLException {
        if (metadataPatchReady) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS metadata_patch (
                    source_path VARCHAR,
                    entry_path VARCHAR,
                    seq INTEGER,
                    entry_time TIMESTAMP,
                    message VARCHAR,
                    formatting BIGINT[],
                    minecraft_user VARCHAR,
                    server_or_world VARCHAR
                )
                """);
        }
        metadataAppender = connection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "metadata_patch");
        metadataPatchReady = true;
    }

    private static void prepareNormalizedPatches(Statement statement) throws SQLException {
        EntryMatch.expandSeconds(statement, """
            SELECT
                source_path,
                entry_path,
                seq,
                entry_time,
                date_trunc('second', entry_time) AS entry_second,
                %s AS text,
                formatting,
                minecraft_user,
                server_or_world
            FROM metadata_patch
            """.formatted(EntryMatch.normalizedText("message")),
            "metadata_patch_norm", "metadata_patch_seconds");
    }

    private void updateLogFileUsers() throws SQLException {
        String session = SourceKind.SESSION.name();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                UPDATE log_file SET minecraft_user = CASE
                    WHEN log_file.source_kind = '%s' THEN coalesce(log_file.minecraft_user, p.minecraft_user)
                    ELSE p.minecraft_user
                END
                FROM (
                    SELECT source_path, entry_path, minecraft_user
                    FROM metadata_patch_norm
                    WHERE minecraft_user IS NOT NULL
                    QUALIFY row_number() OVER (PARTITION BY source_path, entry_path ORDER BY seq) = 1
                ) p
                WHERE log_file.source_path = p.source_path AND log_file.entry_path = p.entry_path
                """.formatted(session));
            statement.execute("""
                UPDATE log_file SET minecraft_user = coalesce(log_file.minecraft_user, p.minecraft_user)
                FROM (
                    SELECT e.file_id, p.minecraft_user
                    FROM metadata_patch_seconds p
                    JOIN chat_entry e
                      ON date_trunc('second', e.entry_time) = p.join_second
                     AND %s
                    JOIN log_file f ON f.id = e.file_id
                    WHERE p.minecraft_user IS NOT NULL
                      AND f.source_kind = '%s'
                      AND %s
                    QUALIFY row_number() OVER (PARTITION BY e.file_id ORDER BY p.seq) = 1
                ) p
                WHERE log_file.id = p.file_id
                  AND log_file.source_kind = '%s'
                  AND log_file.minecraft_user IS NULL
                """.formatted(EntryMatch.sameAsNormalized("e.message", "p.text"), session,
                    EntryMatch.withinWindow("e.entry_time", "p.entry_time"), session));
        }
    }

    public int writtenFiles() {
        return writtenFiles;
    }

    public long writtenEntries() {
        return writtenEntries;
    }

    /**
     * Drops file entries from this import that repeat a line already in the database: the same message
     * text within {@link EntryMatch#WINDOW_SECONDS} of a row stored before this import started.
     * Identical lines written by this import, including repeats inside one file, are kept. Live session
     * rows are never dropped.
     *
     * @return the number of removed entries
     */
    public long deduplicate() throws SQLException {
        flushAppenders();
        String session = SourceKind.SESSION.name();
        try (Statement statement = connection.createStatement()) {
            EntryMatch.expandSeconds(statement, """
                SELECT
                    e.rowid AS rid,
                    e.file_id,
                    e.entry_time,
                    date_trunc('second', e.entry_time) AS entry_second,
                    %s AS text
                FROM chat_entry e
                JOIN log_file f ON f.id = e.file_id
                WHERE e.file_id >= %d
                  AND f.source_kind <> '%s'
                """.formatted(EntryMatch.normalizedText("e.message"), sessionStartId, session),
                "import_dup_norm", "import_dup_seconds");
            long[] removed = deleteDuplicates(statement, """
                DELETE FROM chat_entry WHERE rowid IN (
                    SELECT DISTINCT n.rid
                    FROM import_dup_seconds n
                    JOIN chat_entry e
                      ON date_trunc('second', e.entry_time) = n.join_second
                     AND %s
                    WHERE e.file_id < %d
                      AND %s
                ) RETURNING file_id
                """.formatted(EntryMatch.sameAsNormalized("e.message", "n.text"), sessionStartId,
                    EntryMatch.withinWindow("n.entry_time", "e.entry_time")));
            statement.execute("DROP TABLE IF EXISTS import_dup_seconds");
            statement.execute("DROP TABLE IF EXISTS import_dup_norm");
            if (removed[0] > 0) writtenFiles -= refreshFileAggregates(statement);
            writtenEntries -= removed[1];
            return removed[0];
        }
    }

    /**
     * @return {@code [total deleted, deleted from this import]}
     */
    private long[] deleteDuplicates(Statement statement, String sql) throws SQLException {
        long total = 0;
        long fromThisImport = 0;
        try (ResultSet deleted = statement.executeQuery(sql)) {
            while (deleted.next()) {
                total++;
                if (deleted.getLong(1) >= sessionStartId) fromThisImport++;
            }
        }
        return new long[]{total, fromThisImport};
    }

    /**
     * Recomputes per-file entry counts after rows were deleted, and drops files left without any entries.
     * {@code start_time} / {@code end_time} bound every logged line, not just chat entries, so they are left untouched.
     *
     * @return how many of this session's files were dropped because every one of their entries turned out to be a
     *         duplicate
     */
    private int refreshFileAggregates(Statement statement) throws SQLException {
        statement.execute("""
            UPDATE log_file SET entry_count = coalesce(stats.count, 0)
            FROM (
                SELECT f.id AS file_id, count(e.entry_time) AS count
                FROM log_file f LEFT JOIN chat_entry e ON e.file_id = f.id
                GROUP BY f.id
            ) stats
            WHERE log_file.id = stats.file_id""");

        List<Long> emptyIds = new ArrayList<>();
        List<String> emptyLocations = new ArrayList<>();
        int emptySessionFiles = 0;
        try (ResultSet result = statement.executeQuery(
            "SELECT id, source_path, entry_path FROM log_file WHERE entry_count = 0 AND source_kind <> '"
                + SourceKind.SESSION.name() + "'")) {
            while (result.next()) {
                long id = result.getLong(1);
                if (keepEvenIfEmpty.contains(id)) continue;
                emptyIds.add(id);
                emptyLocations.add(locationKey(result.getString(2), result.getString(3)));
                if (id >= sessionStartId) emptySessionFiles++;
            }
        }
        emptyLocations.forEach(existingLocations::remove);
        for (long id : emptyIds) {
            statement.execute("DELETE FROM log_file WHERE id = " + id);
        }
        return emptySessionFiles;
    }

    private void deleteFile(long fileId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM chat_entry WHERE file_id = ?")) {
            delete.setLong(1, fileId);
            delete.execute();
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM log_file WHERE id = ?")) {
            delete.setLong(1, fileId);
            delete.execute();
        }
    }

    private void flushAppenders() throws SQLException {
        fileAppender.flush();
        entryAppender.flush();
        bufferedEntries = 0;
    }

    private void flushMetadataAppender() throws SQLException {
        if (metadataAppender == null) return;
        metadataAppender.flush();
        metadataAppender.close();
        metadataAppender = null;
    }

    @Override
    public void close() throws SQLException {
        fileAppender.close();
        entryAppender.close();
        flushMetadataAppender();
        persistSeen();
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS metadata_patch");
            statement.execute("COMMIT");
        }
    }

    private void persistSeen() throws SQLException {
        if (pendingSeen.isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO import_seen (source_path, entry_path, content_hash) VALUES (?, ?, ?)
            ON CONFLICT (source_path, entry_path) DO UPDATE SET
                content_hash = coalesce(excluded.content_hash, import_seen.content_hash)
            """)) {
            for (SeenLocation location : pendingSeen) {
                insert.setString(1, location.sourcePath());
                insert.setString(2, location.entryPath());
                insert.setString(3, location.contentHash());
                insert.execute();
            }
        }
        pendingSeen.clear();
    }
}
