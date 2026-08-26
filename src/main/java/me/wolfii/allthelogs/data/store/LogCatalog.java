package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.duckdb.DuckDBConnection;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * {@link ChatLog} rows keyed by {@code log_file.id}. The table is tiny next to {@code chat_entry}, so queries
 * load it wholesale instead of binding a huge {@code IN} list of file ids.
 */
public final class LogCatalog {
    private final DuckDBConnection connection;
    private ChatLog[] byId = new ChatLog[0];
    private boolean loaded;

    public LogCatalog(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * Drops the cached rows so the next {@link #ensureLoaded()} picks up imports and session updates.
     */
    public void invalidate() {
        loaded = false;
        byId = new ChatLog[0];
    }

    public void ensureLoaded() throws SQLException {
        if (loaded) return;
        ChatLog[] next = new ChatLog[32];
        String sql = """
            SELECT id, file_name, source_kind, source_path, entry_path, log_date, minecraft_version,
                   start_time, end_time
            FROM log_file""";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                int id = Math.toIntExact(result.getLong(1));
                if (id >= next.length) {
                    int cap = next.length;
                    while (cap <= id) cap += cap >> 1;
                    next = Arrays.copyOf(next, cap);
                }
                next[id] = readChatLog(result, 2);
            }
        }
        byId = next;
        loaded = true;
    }

    public ChatLog get(long fileId) {
        int id = Math.toIntExact(fileId);
        if (id < 0 || id >= byId.length) return null;
        return byId[id];
    }

    public static ChatLog readChatLog(ResultSet result, int offset) throws SQLException {
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
}
