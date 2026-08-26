package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.ChatQuery;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.store.LogCatalog;
import me.wolfii.allthelogs.data.store.MessageDictionary;
import org.duckdb.DuckDBChunkedResult;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDataChunkReader;
import org.duckdb.DuckDBPreparedStatement;
import org.duckdb.DuckDBReadableVector;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs {@link ChatQuery} against an open store and maps rows to {@link ChatEntry} / {@link ChatLog}.
 */
public final class ChatQueries {
    private static final long MICROS_PER_SECOND = 1_000_000L;

    private final DuckDBConnection connection;
    private final MessageDictionary messages;
    private final LogCatalog logs;

    public ChatQueries(DuckDBConnection connection, MessageDictionary messages, LogCatalog logs) {
        this.connection = connection;
        this.messages = messages;
        this.logs = logs;
    }

    /**
     * Returns every entry matching {@code query}, with chat-log metadata attached.
     * Only logs that appear in the result are resolved from the catalog.
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
            if (rows.size == 0) return entries;
            messages.prefetch(rows.messageIds, rows.size);
            logs.ensureLoaded();
            rows.toEntries(logs, messages, entries);
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
                logs.add(LogCatalog.readChatLog(result, 1));
            }
        } catch (SQLException e) {
            throw new LogDataException("could not read chat log metadata", e);
        }
        return logs;
    }

    static LocalDateTime fromEpochMicros(long micros) {
        long seconds = Math.floorDiv(micros, MICROS_PER_SECOND);
        int nanos = Math.multiplyExact((int) Math.floorMod(micros, MICROS_PER_SECOND), 1_000);
        return LocalDateTime.ofEpochSecond(seconds, nanos, ZoneOffset.UTC);
    }

    /**
     * Columnar buffer for one query result. Message text and chat-log metadata are resolved in Java after the
     * chunked stream finishes, so DuckDB never copies VARCHAR values onto every row.
     */
    private static final class ResultRows {
        private long[] fileIds;
        private long[] timestampMicros;
        private int[] lineIndices;
        private int[] messageIds;
        private int size;

        ResultRows(int capacity) {
            int cap = Math.max(16, capacity);
            this.fileIds = new long[cap];
            this.timestampMicros = new long[cap];
            this.lineIndices = new int[cap];
            this.messageIds = new int[cap];
        }

        void append(DuckDBDataChunkReader chunk) {
            DuckDBReadableVector ids = chunk.vector(0);
            DuckDBReadableVector times = chunk.vector(1);
            DuckDBReadableVector lines = chunk.vector(2);
            DuckDBReadableVector texts = chunk.vector(3);
            int rows = Math.toIntExact(chunk.rowCount());
            ensureRoom(rows);
            for (int row = 0; row < rows; row++) {
                fileIds[size] = ids.getLong(row);
                timestampMicros[size] = times.getLong(row);
                lineIndices[size] = lines.getInt(row);
                messageIds[size] = Math.toIntExact(texts.getLong(row));
                size++;
            }
        }

        void toEntries(LogCatalog logs, MessageDictionary messages, List<ChatEntry> entries) {
            long previousFileId = Long.MIN_VALUE;
            ChatLog log = null;
            long previousMicros = Long.MIN_VALUE;
            LocalDateTime timestamp = null;
            for (int i = 0; i < size; i++) {
                long fileId = fileIds[i];
                if (fileId != previousFileId) {
                    log = logs.get(fileId);
                    if (log == null) {
                        throw new LogDataException("chat entry references unknown log file " + fileId);
                    }
                    previousFileId = fileId;
                }
                long micros = timestampMicros[i];
                if (micros != previousMicros) {
                    timestamp = fromEpochMicros(micros);
                    previousMicros = micros;
                }
                entries.add(new ChatEntry(log, timestamp, lineIndices[i], messages.get(messageIds[i])));
            }
        }

        private void ensureRoom(int extra) {
            int needed = size + extra;
            if (needed <= fileIds.length) return;
            int cap = fileIds.length;
            while (cap < needed) cap += cap >> 1;
            fileIds = Arrays.copyOf(fileIds, cap);
            timestampMicros = Arrays.copyOf(timestampMicros, cap);
            lineIndices = Arrays.copyOf(lineIndices, cap);
            messageIds = Arrays.copyOf(messageIds, cap);
        }
    }
}
