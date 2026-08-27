package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import me.wolfii.allthelogs.data.store.SessionMarker;
import me.wolfii.allthelogs.data.store.SourceKind;
import org.duckdb.*;

import java.nio.file.Path;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Runs {@link ChatQuery} against an open store and maps rows to {@link ChatEntry} / {@link ChatLog}.
 */
public final class ChatQueries {
    private static final String SELECT_AROUND = """
        SELECT e.file_id, e.entry_time, e.line_index, e.message, to_json(e.formatting)
        FROM chat_entry e
        JOIN log_file f ON f.id = e.file_id
        WHERE f.source_path = ? AND f.entry_path = ? AND e.line_index BETWEEN ? AND ?
        ORDER BY e.line_index""";
    private final DuckDBConnection connection;

    public ChatQueries(DuckDBConnection connection) {
        this.connection = connection;
    }

    private static ChatLog readChatLog(ResultSet result, int offset) throws SQLException {
        return new ChatLog(
            readLogSource(result, offset),
            result.getDate(offset + 4).toLocalDate(),
            result.getString(offset + 5),
            result.getTimestamp(offset + 6).toLocalDateTime(),
            result.getTimestamp(offset + 7).toLocalDateTime(),
            result.getString(offset + 8));
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
            case SESSION -> new LogSource.Session(SessionMarker.idFromEntryPath(entryPath));
        };
    }

    static String storedSourcePath(LogSource source) {
        return switch (source) {
            case LogSource.File file -> file.path().toString();
            case LogSource.Archive archive -> archive.path().toString();
            case LogSource.Session ignored -> "<session>";
        };
    }

    static String storedEntryPath(LogSource source) {
        return switch (source) {
            case LogSource.File ignored -> "";
            case LogSource.Archive archive -> archive.entryPath();
            case LogSource.Session session -> SessionMarker.entryPath(session.id());
        };
    }

    /**
     * Returns every entry matching {@code query}, with chat-log metadata attached.
     * Only logs that appear in the result are loaded.
     * Ordered by timestamp, then file id, then line index, in {@link ChatQuery#sort()} direction
     * (ascending is oldest first, with a higher line index further down when timestamps match).
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
     * Oldest and newest match times for {@code query}, ignoring paging and context.
     */
    public MatchBounds bounds(ChatQuery query) {
        QueryBuilder builder = QueryBuilder.bounds(query);
        try (PreparedStatement prepared = connection.prepareStatement(builder.sql())) {
            builder.bind(prepared);
            try (ResultSet result = prepared.executeQuery()) {
                if (!result.next()) return MatchBounds.empty();
                Timestamp oldest = result.getTimestamp(1);
                Timestamp newest = result.getTimestamp(2);
                int uniqueDates = result.getInt(3);
                if (oldest == null || newest == null) return MatchBounds.empty();
                return new MatchBounds(oldest.toLocalDateTime(), newest.toLocalDateTime(), uniqueDates,
                    List.of(), matchDays(query));
            }
        } catch (SQLException | RuntimeException e) {
            throw new LogDataException("could not read match bounds for " + query, e);
        }
    }

    /**
     * Number of matching entries for {@code query}. Honours offset and limit; ignores context lines.
     */
    public long matches(ChatQuery query) {
        QueryBuilder builder = QueryBuilder.matches(query);
        try (PreparedStatement prepared = connection.prepareStatement(builder.sql())) {
            builder.bind(prepared);
            try (ResultSet result = prepared.executeQuery()) {
                if (!result.next()) return 0;
                return result.getLong(1);
            }
        } catch (SQLException | RuntimeException e) {
            throw new LogDataException("could not count matches for " + query, e);
        }
    }

    private List<MatchDay> matchDays(ChatQuery query) {
        QueryBuilder builder = QueryBuilder.dates(query);
        List<MatchDay> days = new ArrayList<>();
        try (PreparedStatement prepared = connection.prepareStatement(builder.sql())) {
            builder.bind(prepared);
            try (ResultSet result = prepared.executeQuery()) {
                while (result.next()) {
                    Date date = result.getDate(1);
                    Timestamp oldest = result.getTimestamp(2);
                    Timestamp newest = result.getTimestamp(3);
                    long matches = result.getLong(4);
                    if (date == null || oldest == null || newest == null) continue;
                    days.add(new MatchDay(date.toLocalDate(), oldest.toLocalDateTime(), newest.toLocalDateTime(),
                        matches));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw new LogDataException("could not read match dates for " + query, e);
        }
        return days;
    }

    /**
     * Entries from {@code log} whose line index is within {@code radius} of {@code lineIndex}, inclusive.
     */
    public List<ChatEntry> around(ChatLog log, int lineIndex, int radius) {
        Objects.requireNonNull(log, "log");
        int from = Math.max(0, lineIndex - Math.max(0, radius));
        int to = lineIndex + Math.max(0, radius);
        String sourcePath = storedSourcePath(log.source());
        String entryPath = storedEntryPath(log.source());
        String sql = SELECT_AROUND;
        List<ChatEntry> entries = new ArrayList<>();
        try {
            ResultRows rows = new ResultRows(Math.max(16, to - from + 1));
            try (PreparedStatement prepared = connection.prepareStatement(sql)) {
                prepared.setString(1, sourcePath);
                prepared.setString(2, entryPath);
                prepared.setInt(3, from);
                prepared.setInt(4, to);
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
        } catch (SQLException | RuntimeException e) {
            throw new LogDataException("could not read lines around " + lineIndex + " in " + log.source(), e);
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
                   start_time, end_time, minecraft_user
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
    public LogStoreMetadata metadata(long databaseSizeBytes) {
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
            return new LogStoreMetadata(versions, firstLogDate, lastLogDate, chatLogCount, chatEntryCount,
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
                   start_time, end_time, minecraft_user
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

    /**
     * Columnar buffer for one query result. Chat-log metadata is joined in Java after the chunked
     * stream finishes, so DuckDB does not repeat file strings on every row.
     */
    private static final class ResultRows {
        private final ArrayList<LocalDateTime> timestamps;
        private final ArrayList<String> messages;
        private final ArrayList<long[]> formattings;
        private final Set<Long> neededFileIds = new HashSet<>();
        private long[] fileIds;
        private int[] lineIndices;
        private int size;

        ResultRows(int capacity) {
            int cap = Math.max(16, capacity);
            this.fileIds = new long[cap];
            this.lineIndices = new int[cap];
            this.timestamps = new ArrayList<>(cap);
            this.messages = new ArrayList<>(cap);
            this.formattings = new ArrayList<>(cap);
        }

        Set<Long> neededFileIds() {
            return neededFileIds;
        }

        void append(DuckDBDataChunkReader chunk) {
            DuckDBReadableVector ids = chunk.vector(0);
            DuckDBReadableVector times = chunk.vector(1);
            DuckDBReadableVector lines = chunk.vector(2);
            DuckDBReadableVector texts = chunk.vector(3);
            DuckDBReadableVector formats = chunk.vector(4);
            int rows = Math.toIntExact(chunk.rowCount());
            ensureRoom(rows);
            long previousFileId = size == 0 ? Long.MIN_VALUE : fileIds[size - 1];
            for (int row = 0; row < rows; row++) {
                long fileId = ids.getLong(row);
                fileIds[size] = fileId;
                lineIndices[size] = lines.getInt(row);
                timestamps.add(times.getLocalDateTime(row));
                messages.add(texts.getString(row));
                formattings.add(formats.isNull(row) ? null : PackedFormatting.fromSqlLiteral(formats.getString(row)));
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
                entries.add(new ChatEntry(log, timestamps.get(i), lineIndices[i], messages.get(i), formattings.get(i)));
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
