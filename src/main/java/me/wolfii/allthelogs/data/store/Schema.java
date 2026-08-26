package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database layout. Chat text lives in {@code chat_message} so {@code chat_entry} stays a narrow integer table:
 * text filters scan unique strings, and results can be pulled as primitive ids. {@code chat_entry} has no
 * ART index, because one would prevent DuckDB from reusing table blocks when a log file is re-imported.
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
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS log_file_location ON log_file (source_path, entry_path)");
        if (!tableExists(statement, "chat_entry")) {
            createMessageTable(statement);
            createEntryTable(statement);
            return;
        }
        if (columnExists(statement, "chat_entry", "message")) {
            migrateInlineMessages(statement);
            return;
        }
        createMessageTable(statement);
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
            SELECT file_id, line_index, entry_time, message_id
            FROM chat_entry
            ORDER BY entry_time, file_id, line_index""");
        statement.execute("DROP TABLE chat_entry");
        statement.execute("ALTER TABLE chat_entry_sorted RENAME TO chat_entry");
    }

    private static void createMessageTable(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE IF NOT EXISTS chat_message (
                id BIGINT PRIMARY KEY,
                message VARCHAR NOT NULL
            )""");
    }

    private static void createEntryTable(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE chat_entry (
                file_id BIGINT NOT NULL,
                line_index INTEGER NOT NULL,
                entry_time TIMESTAMP NOT NULL,
                message_id BIGINT NOT NULL
            )""");
    }

    /**
     * Older databases stored the message VARCHAR on every chat row. Rewrite them onto the dictionary once.
     */
    private static void migrateInlineMessages(Statement statement) throws SQLException {
        createMessageTable(statement);
        statement.execute("""
            INSERT INTO chat_message (id, message)
            SELECT row_number() OVER (), message
            FROM (SELECT DISTINCT message FROM chat_entry)
            WHERE NOT EXISTS (SELECT 1 FROM chat_message)""");
        statement.execute("DROP TABLE IF EXISTS chat_entry_migrated");
        statement.execute("""
            CREATE TABLE chat_entry_migrated AS
            SELECT e.file_id, e.line_index, e.entry_time, m.id AS message_id
            FROM chat_entry e
            INNER JOIN chat_message m ON e.message = m.message
            ORDER BY e.entry_time, e.file_id, e.line_index""");
        statement.execute("DROP TABLE chat_entry");
        statement.execute("ALTER TABLE chat_entry_migrated RENAME TO chat_entry");
    }

    private static boolean tableExists(Statement statement, String table) throws SQLException {
        try (ResultSet result = statement.executeQuery(
            "SELECT 1 FROM information_schema.tables WHERE lower(table_name) = '" + table + "' LIMIT 1")) {
            return result.next();
        }
    }

    private static boolean columnExists(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery(
            "SELECT 1 FROM information_schema.columns WHERE lower(table_name) = '" + table
                + "' AND lower(column_name) = '" + column + "' LIMIT 1")) {
            return result.next();
        }
    }
}
