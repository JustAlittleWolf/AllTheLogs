package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.duckdb.DuckDBDataChunkReader;
import org.duckdb.DuckDBReadableVector;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Columnar buffer for the rows of one entry query, filled chunk by chunk as DuckDB streams them.
 * <p>
 * Chat-log metadata is joined in afterwards, in Java, by {@code file_id}: the alternative is a SQL join that
 * repeats every log's file name and paths on every single row. Query results are ordered by file, so the set of
 * referenced ids only grows when the file changes from one row to the next.
 */
final class EntryRows {
    private final ArrayList<LocalDateTime> timestamps;
    private final ArrayList<String> messages;
    private final ArrayList<long[]> formattings;
    private final Set<Long> referencedFileIds = new HashSet<>();
    private long[] fileIds;
    private int[] lineIndices;
    private int size;

    EntryRows(int expectedRows) {
        int capacity = Math.max(16, expectedRows);
        this.fileIds = new long[capacity];
        this.lineIndices = new int[capacity];
        this.timestamps = new ArrayList<>(capacity);
        this.messages = new ArrayList<>(capacity);
        this.formattings = new ArrayList<>(capacity);
    }

    int size() {
        return size;
    }

    Set<Long> referencedFileIds() {
        return referencedFileIds;
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
                referencedFileIds.add(fileId);
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
        int capacity = fileIds.length;
        while (capacity < needed) capacity += capacity >> 1;
        fileIds = Arrays.copyOf(fileIds, capacity);
        lineIndices = Arrays.copyOf(lineIndices, capacity);
    }
}
