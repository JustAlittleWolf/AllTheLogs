package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.SessionMarker;
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

    private final DuckDBConnection connection;
    private final DuckDBAppender fileAppender;
    private final DuckDBAppender entryAppender;
    private final Map<String, Long> existingLocations = new ConcurrentHashMap<>();
    private final Set<String> knownSessionIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> keepEvenIfEmpty = ConcurrentHashMap.newKeySet();
    /** First file id handed out by this writer; counters and dedup bookkeeping are scoped to this import. */
    private final long sessionStartId;
    private long nextFileId;
    private long bufferedEntries;
    private long writtenEntries;
    private int writtenFiles;

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

    /**
     * Whether a log at this location has already been stored, either by an earlier run or earlier in this one.
     */
    public boolean isAlreadyImported(String sourcePath, String entryPath) {
        return existingLocations.containsKey(locationKey(sourcePath, entryPath));
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
        fileAppender.endRow();

        for (int i = 0; i < times.size(); i++) {
            entryAppender.beginRow();
            entryAppender.append(fileId);
            entryAppender.append(i);
            entryAppender.append(times.get(i));
            entryAppender.append(messages.get(i));
            entryAppender.endRow();
        }

        if (times.isEmpty() && log.resourceManagerReloaded()) keepEvenIfEmpty.add(fileId);
        existingLocations.put(locationKey(log.sourcePath(), log.entryPath()), fileId);
        writtenFiles++;
        writtenEntries += times.size();
        bufferedEntries += times.size();
        if (bufferedEntries >= FLUSH_INTERVAL) flushAppenders();
    }

    public int writtenFiles() {
        return writtenFiles;
    }

    public long writtenEntries() {
        return writtenEntries;
    }

    /**
     * Drops entries that repeat a timestamp and message already stored elsewhere, keeping the earliest imported one.
     * Runs once at the end of an import across the whole store. {@link #writtenFiles()} / {@link #writtenEntries()}
     * only move for rows that belonged to files this session wrote.
     *
     * @return the number of removed entries, across the whole store
     */
    public long deduplicate() throws SQLException {
        flushAppenders();
        long removed = 0;
        long removedFromSession = 0;
        try (Statement statement = connection.createStatement();
             ResultSet deleted = statement.executeQuery("""
                 DELETE FROM chat_entry WHERE rowid IN (
                     SELECT rowid FROM (
                         SELECT rowid, row_number() OVER (
                             PARTITION BY entry_time, message ORDER BY file_id, line_index) AS rn
                         FROM chat_entry
                     ) WHERE rn > 1
                 ) RETURNING file_id""")) {
            while (deleted.next()) {
                removed++;
                if (deleted.getLong(1) >= sessionStartId) removedFromSession++;
            }
            if (removed > 0) writtenFiles -= refreshFileAggregates(statement);
        }
        writtenEntries -= removedFromSession;
        return removed;
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

    @Override
    public void close() throws SQLException {
        fileAppender.close();
        entryAppender.close();
        try (Statement statement = connection.createStatement()) {
            statement.execute("COMMIT");
        }
    }
}
