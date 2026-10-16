package me.wolfii.allthelogs.data.store.write;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Per-file totals derived from {@code chat_entry}, refreshed after rows are deleted.
 */
public final class FileAggregates {
    private FileAggregates() {
    }

    /**
     * Recounts {@code log_file.entry_count} from the stored entries. {@code start_time} and {@code end_time}
     * bound every logged line, not only chat entries, so they are left untouched.
     */
    public static void refreshEntryCounts(Statement statement) throws SQLException {
        statement.execute("""
            UPDATE log_file SET entry_count = coalesce(stats.count, 0)
            FROM (
                SELECT f.id AS file_id, count(e.entry_time) AS count
                FROM log_file f LEFT JOIN chat_entry e ON e.file_id = f.id
                GROUP BY f.id
            ) stats
            WHERE log_file.id = stats.file_id""");
    }
}
