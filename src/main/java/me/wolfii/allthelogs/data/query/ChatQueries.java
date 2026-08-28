package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.store.StoredSources;
import org.duckdb.DuckDBChunkedResult;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBPreparedStatement;

import java.sql.*;
import java.sql.Date;
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
    private static final String SELECT_LOGS = """
        SELECT id, file_name, source_kind, source_path, entry_path, log_date, minecraft_version,
               start_time, end_time, minecraft_user
        FROM log_file WHERE id IN (""";

    private final DuckDBConnection connection;
    private final CatalogQueries catalog;

    public ChatQueries(DuckDBConnection connection) {
        this.connection = connection;
        this.catalog = new CatalogQueries(connection);
    }

    /**
     * Returns every entry matching {@code query}, with chat-log metadata attached.
     * Only logs that appear in the result are loaded.
     * Ordered by timestamp, then file id, then line index, in {@link ChatQuery#sort()} direction
     * (ascending is oldest first, with a higher line index further down when timestamps match).
     *
     * @throws LogDataException if the query is rejected, e.g. because its regex is malformed
     */
    public List<ChatEntry> findEntries(ChatQuery query) {
        rejectUnsupportedRegex(query);
        QueryBuilder builder = QueryBuilder.build(query);
        int expectedRows = query.limit() >= 0 ? (int) Math.min(query.limit(), 8_000_000) : 1024;
        try {
            return readEntries(builder.sql(), builder::bind, expectedRows);
        } catch (LogDataException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw queryFailed("could not run query", query, e);
        }
    }

    /**
     * Entries from {@code log} between {@code lineIndex - before} and {@code lineIndex + after}, inclusive.
     */
    public List<ChatEntry> entriesAround(ChatLog log, int lineIndex, int before, int after) {
        int from = Math.max(0, lineIndex - Math.max(0, before));
        int to = lineIndex + Math.max(0, after);
        String sourcePath = StoredSources.sourcePath(log.source());
        String entryPath = StoredSources.entryPath(log.source());
        try {
            return readEntries(SELECT_AROUND, statement -> {
                statement.setString(1, sourcePath);
                statement.setString(2, entryPath);
                statement.setInt(3, from);
                statement.setInt(4, to);
            }, Math.max(16, to - from + 1));
        } catch (SQLException | RuntimeException e) {
            throw new LogDataException("could not read lines around " + lineIndex + " in " + log.source(), e);
        }
    }

    /**
     * Unpaged match metadata: total count, first/last times, and per-day counts. One {@code GROUP BY} date
     * scan, not a fetch of every matching row.
     */
    public MatchSummary summarizeMatches(ChatQuery query) {
        rejectUnsupportedRegex(query);
        QueryBuilder builder = QueryBuilder.summary(query);
        List<MatchDay> days = new ArrayList<>();
        try (PreparedStatement prepared = connection.prepareStatement(builder.sql())) {
            builder.bind(prepared);
            try (ResultSet result = prepared.executeQuery()) {
                while (result.next()) {
                    Date date = result.getDate(1);
                    Timestamp oldest = result.getTimestamp(2);
                    Timestamp newest = result.getTimestamp(3);
                    if (date == null || oldest == null || newest == null) continue;
                    days.add(new MatchDay(date.toLocalDate(), oldest.toLocalDateTime(), newest.toLocalDateTime(),
                        result.getLong(4)));
                }
            }
        } catch (SQLException | RuntimeException e) {
            throw queryFailed("could not read match dates", query, e);
        }
        return MatchSummary.of(days);
    }

    /**
     * Number of matching entries for {@code query}. Honours offset and limit; ignores context lines.
     */
    public long countMatches(ChatQuery query) {
        rejectUnsupportedRegex(query);
        QueryBuilder builder = QueryBuilder.matches(query);
        try (PreparedStatement prepared = connection.prepareStatement(builder.sql())) {
            builder.bind(prepared);
            try (ResultSet result = prepared.executeQuery()) {
                if (!result.next()) return 0;
                return result.getLong(1);
            }
        } catch (SQLException | RuntimeException e) {
            throw queryFailed("could not count matches", query, e);
        }
    }

    /**
     * Returns every imported chat log, ordered by date.
     */
    public List<ChatLog> chatLogs() {
        return catalog.chatLogs();
    }

    /**
     * Summarises the stored logs, using {@code databaseSizeBytes} supplied by the caller so file-backed stores can
     * report on-disk size including the write-ahead log.
     */
    public LogStoreMetadata metadata(long databaseSizeBytes) {
        return catalog.metadata(databaseSizeBytes);
    }

    /**
     * DuckDB's estimate of how much memory the in-memory database occupies.
     */
    public long reportedDatabaseSize() {
        return catalog.reportedDatabaseSize();
    }

    /**
     * Streams an entry query in chunks, then joins the chat logs its rows referenced.
     * <p>
     * The join has to happen after the chunked stream is closed: DuckDB will not run a second query on the same
     * connection while a chunked result is open. Buffering the columns first also keeps DuckDB from repeating
     * every log's file strings on every row.
     */
    private List<ChatEntry> readEntries(String sql, Binder binder, int expectedRows) throws SQLException {
        EntryRows rows = new EntryRows(expectedRows);
        try (PreparedStatement prepared = connection.prepareStatement(sql)) {
            binder.bind(prepared);
            DuckDBPreparedStatement statement = prepared.unwrap(DuckDBPreparedStatement.class);
            try (DuckDBChunkedResult result = statement.query()) {
                while (result.nextChunk()) {
                    rows.append(result.chunk());
                }
            }
        }
        Set<Long> fileIds = rows.referencedFileIds();
        Map<Long, ChatLog> logsById = HashMap.newHashMap(fileIds.size());
        if (!fileIds.isEmpty()) {
            loadLogs(logsById, fileIds);
        }
        List<ChatEntry> entries = new ArrayList<>(rows.size());
        rows.toEntries(logsById, entries);
        return entries;
    }

    private void loadLogs(Map<Long, ChatLog> logsById, Set<Long> ids) throws SQLException {
        String placeholders = "?,".repeat(ids.size());
        String sql = SELECT_LOGS + placeholders.substring(0, placeholders.length() - 1) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (long id : ids) {
                statement.setLong(index++, id);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    logsById.put(result.getLong(1), CatalogQueries.readChatLog(result, 2));
                }
            }
        }
    }

    /**
     * DuckDB's RE2 engine rejects lookarounds and other Java-only constructs. Catching that here keeps the
     * failure out of SQL, where the native error message is only on the cause and is easy to drop from logs.
     */
    private static void rejectUnsupportedRegex(ChatQuery query) {
        if (query.regex() == null) return;
        String feature = Re2Regex.unsupportedConstruct(query.regex());
        if (feature != null) {
            throw new LogDataException("unsupported regex: " + feature + " is not supported by RE2");
        }
    }

    private static LogDataException queryFailed(String action, ChatQuery query, Exception error) {
        String detail = innermostMessage(error);
        if (query.regex() != null && hasText(detail)) {
            return new LogDataException("could not run regex " + query.regex() + ": " + detail, error);
        }
        if (hasText(detail)) {
            return new LogDataException(action + ": " + detail, error);
        }
        return new LogDataException(action + " " + query, error);
    }

    private static String innermostMessage(Throwable error) {
        String message = null;
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (hasText(current.getMessage())) message = current.getMessage();
        }
        return message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank() && !value.equals("null");
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
