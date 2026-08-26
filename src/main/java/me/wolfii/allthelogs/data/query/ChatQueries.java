package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.ChatQuery;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.StoreMetadata;
import me.wolfii.allthelogs.data.store.SourceKind;
import org.duckdb.DuckDBChunkedResult;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDataChunkReader;
import org.duckdb.DuckDBPreparedStatement;
import org.duckdb.DuckDBReadableVector;

import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs {@link ChatQuery} against an open store and maps rows to {@link ChatEntry} / {@link ChatLog}.
 */
public final class ChatQueries {
    private final DuckDBConnection connection;

    public ChatQueries(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * Returns every entry matching {@code query}, with chat-log metadata attached.
     * Only logs that appear in the result are loaded.
     *
     * @throws LogDataException if the query is rejected, e.g. because its regex is malformed
     */
    public List<ChatEntry> query(ChatQuery query) {
        QueryBuilder builder = QueryBuilder.build(query);
        int initialCapacity = query.limit() >= 0 ? (int) Math.min(query.limit(), 8_000_000) : 1024;
        List<ChatEntry> entries = new ArrayList<>(initialCapacity);
        try {
            ResultRows rows = new ResultRows(initialCapacity);
            try (PreparedStatement prepared = connection.prepareStatement(builder.sql())) {
                builder.bind(prepared);
                DuckDBPreparedStatement statement = prepared.unwrap(DuckDBPreparedStatement.class);
                try (DuckDBChunkedResult result = statement.query()) {
                    while (result.nextChunk()) {
                        rows.append(result.chunk());
                    }
                }
            }
            Map<Long, ChatLog> logsById = HashMap.newHashMap(rows.neededFileIds().size());
            if (!rows.neededFileIds().isEmpty()) {
                loadLogs(logsById, rows.neededFileIds());
            }
            rows.toEntries(logsById, entries);
        } catch (LogDataException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new LogDataException("could not run query " + query, e);
        }
        return entries;
    }

    /**
     * Returns every imported chat log, ordered by date.
     */
    public List<ChatLog> chatLogs() {
        List<ChatLog> logs = new ArrayList<>();
        String sql = """
            SELECT file_name, source_kind, source_path, entry_path, log_date, minecraft_version,
                   start_time, end_time
            FROM log_file ORDER BY log_date, entry_path""";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                logs.add(readChatLog(result, 1));
            }
        } catch (SQLException e) {
            throw new LogDataException("could not read chat log metadata", e);
        }
        return logs;
    }

    /**
     * Summarises the stored logs, using {@code databaseSizeBytes} supplied by the caller so file-backed stores can
     * report on-disk size including the write-ahead log.
     */
    public StoreMetadata metadata(long databaseSizeBytes) {
        try {
            List<String> versions = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                     SELECT minecraft_version
                     FROM log_file
                     GROUP BY minecraft_version
                     ORDER BY MIN(log_date), minecraft_version""")) {
                while (result.next()) {
                    versions.add(result.getString(1));
                }
            }
            long chatLogCount;
            LocalDate firstLogDate;
            LocalDate lastLogDate;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*), MIN(log_date), MAX(log_date) FROM log_file")) {
                result.next();
                chatLogCount = result.getLong(1);
                Date first = result.getDate(2);
                Date last = result.getDate(3);
                firstLogDate = first == null ? null : first.toLocalDate();
                lastLogDate = last == null ? null : last.toLocalDate();
            }
            long chatEntryCount;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM chat_entry")) {
                result.next();
                chatEntryCount = result.getLong(1);
            }
            return new StoreMetadata(versions, firstLogDate, lastLogDate, chatLogCount, chatEntryCount,
                databaseSizeBytes);
        } catch (SQLException e) {
            throw new LogDataException("could not read store metadata", e);
        }
    }

    /**
     * DuckDB's estimate of how much memory the in-memory database occupies.
     */
    public long reportedDatabaseSize() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT COALESCE(SUM(memory_usage_bytes), 0) FROM duckdb_memory()")) {
            if (!result.next()) {
                throw new LogDataException("could not read database size");
            }
            return result.getLong(1);
        } catch (SQLException e) {
            throw new LogDataException("could not read database size", e);
        }
    }

    /**
     * Loads chat logs for the file ids that appear in a query result. This must run after the
     * chunked entry stream is closed: DuckDB will not run a second query on the same connection
     * while a chunked result is open.
     */
    private void loadLogs(Map<Long, ChatLog> logsById, Set<Long> ids) throws SQLException {
        String placeholders = "?,".repeat(ids.size());
        String sql = """
            SELECT id, file_name, source_kind, source_path, entry_path, log_date, minecraft_version,
                   start_time, end_time
            FROM log_file WHERE id IN (""" + placeholders.substring(0, placeholders.length() - 1) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (long id : ids) {
                statement.setLong(index++, id);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    logsById.put(result.getLong(1), readChatLog(result, 2));
                }
            }
        }
    }

    private static ChatLog readChatLog(ResultSet result, int offset) throws SQLException {
        return new ChatLog(
            readLogSource(result, offset),
            result.getDate(offset + 4).toLocalDate(),
            result.getString(offset + 5),
            result.getTimestamp(offset + 6).toLocalDateTime(),
            result.getTimestamp(offset + 7).toLocalDateTime());
    }

    private static LogSource readLogSource(ResultSet result, int offset) throws SQLException {
        String kind = result.getString(offset + 1);
        String path = result.getString(offset + 2);
        String entryPath = result.getString(offset + 3);
        SourceKind sourceKind;
        try {
            sourceKind = SourceKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new SQLException("unknown source kind: " + kind, e);
        }
        return switch (sourceKind) {
            case FILE -> new LogSource.File(Path.of(path));
            case ARCHIVE -> new LogSource.Archive(Path.of(path), entryPath);
            case SESSION -> new LogSource.Session();
        };
    }

    /**
     * Columnar buffer for one query result. Chat-log metadata is joined in Java after the chunked
     * stream finishes, so DuckDB does not repeat file strings on every row.
     */
    private static final class ResultRows {
        private long[] fileIds;
        private int[] lineIndices;
        private final ArrayList<LocalDateTime> timestamps;
        private final ArrayList<String> messages;
        private final Set<Long> neededFileIds = new HashSet<>();
        private int size;

        ResultRows(int capacity) {
            int cap = Math.max(16, capacity);
            this.fileIds = new long[cap];
            this.lineIndices = new int[cap];
            this.timestamps = new ArrayList<>(cap);
            this.messages = new ArrayList<>(cap);
        }

        Set<Long> neededFileIds() {
            return neededFileIds;
        }

        void append(DuckDBDataChunkReader chunk) {
            DuckDBReadableVector ids = chunk.vector(0);
            DuckDBReadableVector times = chunk.vector(1);
            DuckDBReadableVector lines = chunk.vector(2);
            DuckDBReadableVector texts = chunk.vector(3);
            int rows = Math.toIntExact(chunk.rowCount());
            ensureRoom(rows);
            long previousFileId = size == 0 ? Long.MIN_VALUE : fileIds[size - 1];
            for (int row = 0; row < rows; row++) {
                long fileId = ids.getLong(row);
                fileIds[size] = fileId;
                lineIndices[size] = lines.getInt(row);
                timestamps.add(times.getLocalDateTime(row));
                messages.add(texts.getString(row));
                if (fileId != previousFileId) {
                    neededFileIds.add(fileId);
                    previousFileId = fileId;
                }
                size++;
            }
        }

        void toEntries(Map<Long, ChatLog> logsById, List<ChatEntry> entries) {
            long previousFileId = Long.MIN_VALUE;
            ChatLog log = null;
            for (int i = 0; i < size; i++) {
                long fileId = fileIds[i];
                if (fileId != previousFileId) {
                    log = logsById.get(fileId);
                    if (log == null) {
                        throw new LogDataException("chat entry references unknown log file " + fileId);
                    }
                    previousFileId = fileId;
                }
                entries.add(new ChatEntry(log, timestamps.get(i), lineIndices[i], messages.get(i)));
            }
        }

        private void ensureRoom(int extra) {
            int needed = size + extra;
            if (needed <= fileIds.length) return;
            int cap = fileIds.length;
            while (cap < needed) cap += cap >> 1;
            fileIds = Arrays.copyOf(fileIds, cap);
            lineIndices = Arrays.copyOf(lineIndices, cap);
        }
    }
}
