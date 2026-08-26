package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database layout. {@code chat_entry} has no index: both access patterns are full scans, and an ART index would
 * prevent DuckDB from reusing table blocks when a log file is re-imported.
 */
public final class Schema {
    private Schema() {
    }

    public static void create(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE IF NOT EXISTS log_file (
                id BIGINT PRIMARY KEY,
                file_name VARCHAR NOT NULL,
                source_kind VARCHAR NOT NULL,
                source_path VARCHAR NOT NULL,
                entry_path VARCHAR NOT NULL,
                log_date DATE NOT NULL,
                minecraft_version VARCHAR NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP NOT NULL,
                entry_count BIGINT NOT NULL
            )""");
        statement.execute("""
            CREATE TABLE IF NOT EXISTS chat_entry (
                file_id BIGINT NOT NULL,
                line_index INTEGER NOT NULL,
                entry_time TIMESTAMP NOT NULL,
                message VARCHAR NOT NULL
            )""");
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS log_file_location ON log_file (source_path, entry_path)");
    }

    /**
     * Rewrites {@code chat_entry} in timestamp order so row-group zone maps can skip data on range scans and
     * {@code ORDER BY entry_time, file_id, line_index} can stream instead of sorting the whole result.
     */
    public static void clusterEntries(Statement statement) throws SQLException {
        long count;
        try (ResultSet result = statement.executeQuery("SELECT count(*) FROM chat_entry")) {
            result.next();
            count = result.getLong(1);
        }
        if (count == 0) return;
        statement.execute("DROP TABLE IF EXISTS chat_entry_sorted");
        statement.execute("""
            CREATE TABLE chat_entry_sorted AS
            SELECT file_id, line_index, entry_time, message
            FROM chat_entry
            ORDER BY entry_time, file_id, line_index""");
        statement.execute("DROP TABLE chat_entry");
        statement.execute("ALTER TABLE chat_entry_sorted RENAME TO chat_entry");
    }
}
