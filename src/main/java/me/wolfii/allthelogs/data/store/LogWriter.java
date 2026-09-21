package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes parsed logs into the database on a single thread. DuckDB's appender is thread-confined, so import
 * parallelism lives entirely in the parsing stage that feeds this writer.
 */
public final class LogWriter implements AutoCloseable {
    private static final int FLUSH_INTERVAL = 100_000;
    /**
     * File-imported lines that repeat a live session line with the same text this close are dropped.
     * Log files write a linebreak as the two characters {@code \n}; live capture stores a real newline.
     */
    static final int LIVE_DUPLICATE_WINDOW_SECONDS = 3;

    /**
     * SQL that treats a stored {@code \n} pair as the same character as a live newline.
     */
    private static String sameChatText(String left, String right) {
        return "replace(" + left + ", chr(92) || 'n', chr(10)) = replace(" + right
            + ", chr(92) || 'n', chr(10))";
    }

    private final DuckDBConnection connection;
    private final DuckDBAppender fileAppender;
    private final DuckDBAppender entryAppender;
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
     * {@link #LIVE_DUPLICATE_WINDOW_SECONDS}. Formatting is written only when the stored line has none
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
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO metadata_patch (source_path, entry_path, seq, entry_time, message, formatting,
                minecraft_user, server_or_world)
            VALUES (?, ?, ?, ?, ?, CAST(? AS BIGINT[]), ?, ?)
            """)) {
            for (int i = 0; i < times.size(); i++) {
                insert.setString(1, log.sourcePath());
                insert.setString(2, log.entryPath());
                insert.setInt(3, i);
                insert.setTimestamp(4, Timestamp.valueOf(times.get(i)));
                insert.setString(5, messages.get(i));
                long[] formatting = formattings != null && i < formattings.size() ? formattings.get(i) : null;
                String literal = PackedFormatting.toSqlLiteral(formatting);
                if (literal == null) {
                    insert.setNull(6, Types.VARCHAR);
                } else {
                    insert.setString(6, literal);
                }
                String user = users != null && i < users.size() ? users.get(i) : log.minecraftUser();
                if (user == null) {
                    insert.setNull(7, Types.VARCHAR);
                } else {
                    insert.setString(7, user);
                }
                String place = places != null && i < places.size() ? places.get(i) : null;
                if (place == null) {
                    insert.setNull(8, Types.VARCHAR);
                } else {
                    insert.setString(8, place);
                }
                insert.execute();
            }
        }
        metadataPatchFiles++;
    }

    /**
     * Applies every queued {@link #updateMetadata} row in one pass over {@code chat_entry}.
     */
    public void applyMetadataPatches(ImportOptions options) throws SQLException {
        if (!metadataPatchReady || metadataPatchFiles == 0 || !options.updatesAnyMetadata()) {
            return;
        }
        String window = String.valueOf(LIVE_DUPLICATE_WINDOW_SECONDS);
        String formattingFlag = options.updateFormatting() ? "true" : "false";
        String userFlag = options.updateMinecraftUser() ? "true" : "false";
        String serverFlag = options.updateMinecraftServer() ? "true" : "false";
        String session = SourceKind.SESSION.name();
        try (Statement update = connection.createStatement()) {
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
                            e.rowid AS rid,
                            f.source_kind AS kind,
                            p.formatting AS new_formatting,
                            p.minecraft_user AS new_user,
                            p.server_or_world AS new_place,
                            row_number() OVER (
                                PARTITION BY e.rowid
                                ORDER BY
                                    CASE WHEN f.source_path = p.source_path AND f.entry_path = p.entry_path
                                        AND e.line_index = p.seq
                                        THEN 0 ELSE 1 END,
                                    abs(date_diff('millisecond', e.entry_time, p.entry_time)),
                                    p.seq
                            ) AS rn
                        FROM chat_entry e
                        JOIN log_file f ON f.id = e.file_id
                        JOIN metadata_patch p
                          ON (f.source_path = p.source_path AND f.entry_path = p.entry_path
                              AND e.line_index = p.seq)
                          OR (%s
                              AND abs(date_diff('millisecond', e.entry_time, p.entry_time)) <= %s * 1000)
                    ) ranked
                    WHERE rn = 1
                ) m
                WHERE chat_entry.rowid = m.rid
                """.formatted(formattingFlag, userFlag, session, serverFlag, session,
                    sameChatText("e.message", "p.message"), window));
            if (updated > 0 && options.updateMinecraftUser()) {
                updateLogFileUsers();
            }
            if (updated > 0) {
                writtenFiles += metadataPatchFiles;
                writtenEntries += updated;
            }
        }
        metadataPatchFiles = 0;
    }

    private void ensureMetadataPatchTable() throws SQLException {
        if (metadataPatchReady) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE IF NOT EXISTS metadata_patch (
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
        metadataPatchReady = true;
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
                    FROM metadata_patch
                    WHERE minecraft_user IS NOT NULL
                    QUALIFY row_number() OVER (PARTITION BY source_path, entry_path ORDER BY seq) = 1
                ) p
                WHERE log_file.source_path = p.source_path AND log_file.entry_path = p.entry_path
                """.formatted(session));
            statement.execute("""
                UPDATE log_file SET minecraft_user = coalesce(log_file.minecraft_user, p.minecraft_user)
                FROM (
                    SELECT e.file_id, p.minecraft_user
                    FROM chat_entry e
                    JOIN metadata_patch p ON %s
                    WHERE p.minecraft_user IS NOT NULL
                      AND abs(date_diff('millisecond', e.entry_time, p.entry_time))
                          <= %d * 1000
                    QUALIFY row_number() OVER (PARTITION BY e.file_id ORDER BY p.seq) = 1
                ) p
                WHERE log_file.id = p.file_id
                  AND log_file.source_kind = '%s'
                  AND log_file.minecraft_user IS NULL
                """.formatted(sameChatText("e.message", "p.message"), LIVE_DUPLICATE_WINDOW_SECONDS, session));
        }
    }

    public int writtenFiles() {
        return writtenFiles;
    }

    public long writtenEntries() {
        return writtenEntries;
    }

    /**
     * Drops entries from imported files that duplicate an existing entry: the same message in the same
     * second (file or live), or the same message as a live session line within
     * {@link #LIVE_DUPLICATE_WINDOW_SECONDS}. Live session entries are never dropped.
     *
     * @return the number of removed entries, across the whole store
     */
    public long deduplicate() throws SQLException {
        flushAppenders();
        long removed = 0;
        long removedFromThisImport = 0;
        try (Statement statement = connection.createStatement()) {
            DeletedRows sameSecond = deleteDuplicates(statement, """
                DELETE FROM chat_entry WHERE rowid IN (
                    SELECT DISTINCT e.rowid
                    FROM chat_entry e
                    JOIN log_file f ON f.id = e.file_id
                    JOIN chat_entry o
                      ON o.file_id < e.file_id
                     AND date_trunc('second', o.entry_time) = date_trunc('second', e.entry_time)
                     AND %s
                    WHERE e.file_id >= %d
                      AND f.source_kind <> '%s'
                ) RETURNING file_id""".formatted(
                sameChatText("o.message", "e.message"), sessionStartId, SourceKind.SESSION.name()));
            DeletedRows nearLive = deleteDuplicates(statement, """
                DELETE FROM chat_entry WHERE rowid IN (
                    SELECT DISTINCT e.rowid
                    FROM chat_entry e
                    JOIN log_file f ON f.id = e.file_id
                    JOIN chat_entry s
                      ON %s
                     AND abs(date_diff('millisecond', s.entry_time, e.entry_time))
                         <= %s * 1000
                    JOIN log_file sf ON sf.id = s.file_id AND sf.source_kind = '%s'
                    WHERE e.file_id >= %d
                      AND f.source_kind <> '%s'
                ) RETURNING file_id""".formatted(
                sameChatText("s.message", "e.message"), LIVE_DUPLICATE_WINDOW_SECONDS,
                SourceKind.SESSION.name(), sessionStartId, SourceKind.SESSION.name()));
            removed = sameSecond.total + nearLive.total;
            removedFromThisImport = sameSecond.fromThisImport + nearLive.fromThisImport;
            if (removed > 0) {
                Set<Long> touched = new HashSet<>();
                touched.addAll(sameSecond.fileIds);
                touched.addAll(nearLive.fileIds);
                writtenFiles -= refreshFileAggregates(statement, touched);
            }
        }
        writtenEntries -= removedFromThisImport;
        return removed;
    }

    private record DeletedRows(long total, long fromThisImport, List<Long> fileIds) {
    }

    private DeletedRows deleteDuplicates(Statement statement, String sql) throws SQLException {
        long total = 0;
        long fromThisImport = 0;
        List<Long> fileIds = new ArrayList<>();
        try (ResultSet deleted = statement.executeQuery(sql)) {
            while (deleted.next()) {
                long fileId = deleted.getLong(1);
                total++;
                fileIds.add(fileId);
                if (fileId >= sessionStartId) fromThisImport++;
            }
        }
        return new DeletedRows(total, fromThisImport, fileIds);
    }

    /**
     * Recomputes per-file entry counts after rows were deleted, and drops files left without any entries.
     * {@code start_time} / {@code end_time} bound every logged line, not just chat entries, so they are left untouched.
     *
     * @return how many of this session's files were dropped because every one of their entries turned out to be a
     *         duplicate
     */
    private int refreshFileAggregates(Statement statement, Set<Long> touchedFileIds) throws SQLException {
        if (touchedFileIds.isEmpty()) return 0;
        String idList = touchedFileIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        statement.execute("""
            UPDATE log_file SET entry_count = coalesce(stats.count, 0)
            FROM (
                SELECT f.id AS file_id, count(e.entry_time) AS count
                FROM log_file f LEFT JOIN chat_entry e ON e.file_id = f.id
                WHERE f.id IN (%s)
                GROUP BY f.id
            ) stats
            WHERE log_file.id = stats.file_id""".formatted(idList));

        List<Long> emptyIds = new ArrayList<>();
        List<String> emptyLocations = new ArrayList<>();
        int emptySessionFiles = 0;
        try (ResultSet result = statement.executeQuery(
            "SELECT id, source_path, entry_path FROM log_file WHERE entry_count = 0 AND id IN ("
                + idList + ") AND source_kind <> '"
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

    @Override
    public void close() throws SQLException {
        fileAppender.close();
        entryAppender.close();
        persistSeen();
        try (Statement statement = connection.createStatement()) {
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
