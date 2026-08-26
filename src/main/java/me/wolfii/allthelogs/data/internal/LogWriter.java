package me.wolfii.allthelogs.data.internal;

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
import java.util.concurrent.ConcurrentHashMap;

/// Writes parsed logs into the database.
///
/// All writes go through one instance on a single thread. DuckDB's appender is thread confined and bulk appending is
/// far cheaper than prepared statement batches, so parallelism in the import pipeline lives entirely in the parsing
/// stage that feeds this writer.
public final class LogWriter implements AutoCloseable {
    /// Rows buffered in the appender before it is flushed, chosen to stay well above DuckDB's 2048 row vector size
    /// while keeping memory bounded.
    private static final int FLUSH_INTERVAL = 100_000;

    private final DuckDBConnection connection;
    private final DuckDBAppender fileAppender;
    private final DuckDBAppender entryAppender;
    /// Read by the discovery thread for skip decisions while the writer thread updates it, hence concurrent.
    private final Map<String, Long> existingLocations = new ConcurrentHashMap<>();
    /// Ids of files written with no chat entries but a `Reloading ResourceManager` line, kept in memory only so the
    /// post dedup cleanup does not drop them the way it drops files whose entries all turned out to be duplicates.
    private final java.util.Set<Long> keepEvenIfEmpty = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /// First file id handed out by this writer, so counters and dedup bookkeeping can be scoped to files this
    /// session actually wrote instead of every file that happens to live in the database, which may include files
    /// from earlier, unrelated import runs against the same store.
    private final long sessionStartId;
    private long nextFileId;
    private long bufferedEntries;
    private long writtenEntries;
    private int writtenFiles;

    public LogWriter(DuckDBConnection connection) throws SQLException {
        this.connection = connection;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id, source_path, entry_path FROM log_file")) {
            while (result.next()) {
                long id = result.getLong(1);
                existingLocations.put(locationKey(result.getString(2), result.getString(3)), id);
                nextFileId = Math.max(nextFileId, id + 1);
            }
        }
        this.sessionStartId = nextFileId;
        // The appender autocommits every 204,800 rows by default, and each of those commits durably syncs to disk.
        // A whole import is one logical unit of work, so wrapping it in an explicit transaction turns flush() into a
        // cheap in-memory materialisation and defers the durable sync to the single commit on close().
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN TRANSACTION");
        }
        this.fileAppender = connection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "log_file");
        this.entryAppender = connection.createAppender(DuckDBConnection.DEFAULT_SCHEMA, "chat_entry");
    }

    private static String locationKey(String sourcePath, String entryPath) {
        return sourcePath + "\u0000" + entryPath;
    }

    /// Whether a log at this location has already been stored, either by an earlier run or earlier in this one.
    public boolean isAlreadyImported(String sourcePath, String entryPath) {
        return existingLocations.containsKey(locationKey(sourcePath, entryPath));
    }

    /// Stores a parsed log, replacing any previously stored log at the same location.
    public void write(PreparedLog log) throws SQLException {
        Long previous = existingLocations.get(locationKey(log.sourcePath(), log.entryPath()));
        if (previous != null) {
            // The replaced rows may still sit in the appender buffers, so flush before deleting them.
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

    /// Drops entries that repeat a timestamp and message already stored elsewhere, keeping the earliest imported one.
    ///
    /// This runs once at the end of an import instead of checking each row as it is written: a set based anti join
    /// lets the database do the work in one pass, and it also catches duplicates between two files of the same run,
    /// which a per row check against already committed data would miss. Duplicates are removed across the whole
    /// store, not just files written this session, so a re-import that duplicates an earlier run's entries still
    /// gets cleaned up; but this writer's own [#writtenFiles()]/[#writtenEntries()] counters only move for rows that
    /// belonged to files this session wrote, so they keep reporting what this import actually contributed.
    ///
    /// @return the number of removed entries, across the whole store
    public long deduplicate() throws SQLException {
        flushAppenders();
        long removed = 0;
        long removedFromSession = 0;
        try (Statement statement = connection.createStatement();
             // The window function over every row is the expensive part, so run it once and let the DELETE stream
             // back which files it touched, instead of running the same scan twice: once to count and once to
             // delete.
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

    /// Recomputes the per file counters after rows were deleted, and drops files left without any entries.
    ///
    /// @return how many of this session's files were dropped because every one of their entries turned out to be a
    ///         duplicate
    private int refreshFileAggregates(Statement statement) throws SQLException {
        // start_time/end_time bound every logged line of the file, not just its chat entries, so
        // deduplicating chat entries must not touch already stored bounds that came from lines with no chat marker;
        // only entry_count needs recomputing here.
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
