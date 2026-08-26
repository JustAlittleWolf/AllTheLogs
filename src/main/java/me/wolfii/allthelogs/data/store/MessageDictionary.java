package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.LogDataException;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBChunkedResult;
import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDataChunkReader;
import org.duckdb.DuckDBPreparedStatement;
import org.duckdb.DuckDBReadableVector;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Interns chat text so {@code chat_entry} can store integer ids instead of repeating VARCHAR values.
 * <p>
 * Text filters then scan this compact table, and query results can resolve strings without a JNI
 * {@code getString} call on every row.
 */
public final class MessageDictionary {
    private static final int PREFETCH_ALL_THRESHOLD = 20_000;
    private static final int IN_BATCH = 1_000;

    private final DuckDBConnection connection;
    private final Map<String, Integer> idsByText = new HashMap<>();
    private String[] textById = new String[32];
    private int nextId = 1;
    private boolean fullyLoaded;
    private DuckDBAppender appender;

    public MessageDictionary(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * Directs newly interned rows at an open appender. Must be cleared before the appender is closed.
     */
    public void attachAppender(DuckDBAppender appender) {
        this.appender = appender;
    }

    public void detachAppender() {
        this.appender = null;
    }

    /**
     * Returns the id for {@code message}, inserting it when it has not been seen before.
     */
    public int intern(String message) throws SQLException {
        ensureLoaded();
        Integer existing = idsByText.get(message);
        if (existing != null) return existing;
        int id = nextId++;
        remember(id, message);
        idsByText.put(message, id);
        if (appender != null) {
            appender.beginRow();
            appender.append((long) id);
            appender.append(message);
            appender.endRow();
        } else {
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO chat_message (id, message) VALUES (?, ?)")) {
                insert.setLong(1, id);
                insert.setString(2, message);
                insert.execute();
            }
        }
        return id;
    }

    /**
     * Loads dictionary rows needed to resolve {@code messageIds[0..size)}.
     */
    public void prefetch(int[] messageIds, int size) throws SQLException {
        if (fullyLoaded || size == 0) return;
        int maxId = 0;
        for (int i = 0; i < size; i++) {
            if (messageIds[i] > maxId) maxId = messageIds[i];
        }
        BitSet missing = new BitSet(maxId + 1);
        int missingCount = 0;
        for (int i = 0; i < size; i++) {
            int id = messageIds[i];
            if (id <= 0) continue;
            if (id < textById.length && textById[id] != null) continue;
            if (!missing.get(id)) {
                missing.set(id);
                missingCount++;
            }
        }
        if (missingCount == 0) return;
        if (missingCount > PREFETCH_ALL_THRESHOLD) {
            ensureLoaded();
            return;
        }
        loadIds(missing, missingCount);
    }

    /**
     * Loads every stored message. Used by intern and by large result sets.
     */
    public void ensureLoaded() throws SQLException {
        if (fullyLoaded) return;
        try (PreparedStatement prepared = connection.prepareStatement("SELECT id, message FROM chat_message")) {
            DuckDBPreparedStatement statement = prepared.unwrap(DuckDBPreparedStatement.class);
            try (DuckDBChunkedResult result = statement.query()) {
                while (result.nextChunk()) {
                    absorb(result.chunk(), true);
                }
            }
        }
        fullyLoaded = true;
    }

    /**
     * The interned text for {@code id}.
     *
     * @throws LogDataException if the id was never stored
     */
    public String get(int id) {
        String text = id > 0 && id < textById.length ? textById[id] : null;
        if (text == null) {
            throw new LogDataException("chat entry references unknown message " + id);
        }
        return text;
    }

    private void loadIds(BitSet ids, int count) throws SQLException {
        int[] batch = new int[Math.min(IN_BATCH, count)];
        int offset = ids.nextSetBit(0);
        int filled = 0;
        while (offset >= 0) {
            if (filled == batch.length) {
                fetchBatch(batch, filled);
                filled = 0;
            }
            batch[filled++] = offset;
            offset = ids.nextSetBit(offset + 1);
        }
        if (filled > 0) fetchBatch(batch, filled);
    }

    private void fetchBatch(int[] batch, int length) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, message FROM chat_message WHERE id IN (");
        for (int i = 0; i < length; i++) {
            if (i > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(')');
        try (PreparedStatement prepared = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < length; i++) {
                prepared.setLong(i + 1, batch[i]);
            }
            DuckDBPreparedStatement statement = prepared.unwrap(DuckDBPreparedStatement.class);
            try (DuckDBChunkedResult result = statement.query()) {
                while (result.nextChunk()) {
                    absorb(result.chunk(), false);
                }
            }
        }
    }

    private void absorb(DuckDBDataChunkReader chunk, boolean internLookup) {
        DuckDBReadableVector ids = chunk.vector(0);
        DuckDBReadableVector texts = chunk.vector(1);
        int rows = Math.toIntExact(chunk.rowCount());
        for (int row = 0; row < rows; row++) {
            int id = Math.toIntExact(ids.getLong(row));
            String message = texts.getString(row);
            remember(id, message);
            if (internLookup) idsByText.put(message, id);
        }
    }

    private void remember(int id, String message) {
        if (id >= textById.length) {
            int cap = textById.length;
            while (cap <= id) cap += cap >> 1;
            textById = Arrays.copyOf(textById, cap);
        }
        textById[id] = message;
        if (id >= nextId) nextId = id + 1;
    }
}
