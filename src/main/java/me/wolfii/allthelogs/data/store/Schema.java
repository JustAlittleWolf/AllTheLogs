package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.DoubleConsumer;

/**
 * Database layout. {@code chat_entry} has no index: both access patterns are full scans, and an ART index would
 * prevent DuckDB from reusing table blocks when a log file is re-imported.
 * {@code chat_entry.formatting} is a {@code BIGINT[]} of packed runs (one {@code long} per range), or NULL.
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
                entry_count BIGINT NOT NULL,
                minecraft_user VARCHAR
            )""");
        statement.execute("""
            CREATE TABLE IF NOT EXISTS chat_entry (
                file_id BIGINT NOT NULL,
                line_index INTEGER NOT NULL,
                entry_time TIMESTAMP NOT NULL,
                message VARCHAR NOT NULL,
                formatting BIGINT[]
            )""");
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS log_file_location ON log_file (source_path, entry_path)");
        statement.execute("""
            CREATE TABLE IF NOT EXISTS import_seen (
                source_path VARCHAR NOT NULL,
                entry_path VARCHAR NOT NULL,
                content_hash VARCHAR,
                PRIMARY KEY (source_path, entry_path)
            )""");
        statement.execute("CREATE INDEX IF NOT EXISTS import_seen_hash ON import_seen (content_hash)");
    }

    /**
     * Rewrites {@code chat_entry} oldest-first so row-group zone maps can skip data on range scans and
     * {@code ORDER BY entry_time, file_id, line_index} can stream instead of sorting the whole result.
     * Same-timestamp lines from one log stay in line-index order, matching {@code ChatQueries#query}.
     */
    public static void clusterEntries(Statement statement) throws SQLException {
        clusterEntries(statement, ignored -> {
        });
    }

    /**
     * Same as {@link #clusterEntries(Statement)}, reporting 0–1 progress as the rewrite moves.
     */
    public static void clusterEntries(Statement statement, DoubleConsumer progress) throws SQLException {
        DoubleConsumer report = progress == null ? ignored -> {
        } : progress;
        long count;
        try (ResultSet result = statement.executeQuery("SELECT count(*) FROM chat_entry")) {
            result.next();
            count = result.getLong(1);
        }
        if (count == 0) {
            report.accept(1d);
            return;
        }
        report.accept(0.1);
        statement.execute("DROP TABLE IF EXISTS chat_entry_sorted");
        statement.execute("""
            CREATE TABLE chat_entry_sorted AS
            SELECT file_id, line_index, entry_time, message, formatting
            FROM chat_entry
            ORDER BY entry_time, file_id, line_index""");
        report.accept(0.75);
        statement.execute("DROP TABLE chat_entry");
        statement.execute("ALTER TABLE chat_entry_sorted RENAME TO chat_entry");
        report.accept(1d);
    }
}
