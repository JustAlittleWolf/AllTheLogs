package me.wolfii.allthelogs.data.internal;

import java.sql.SQLException;
import java.sql.Statement;

/// The database layout. Callers of the public API never see any of this.
///
/// The design is driven by the two access patterns that matter: scanning a date range and scanning for a text match.
/// Entries are stored in one wide table sorted by timestamp, which lets DuckDB skip whole row groups via its zone maps
/// for range queries and keeps text scans a single sequential pass. Per row overhead is minimised by referencing the
/// file through a small integer id instead of repeating its metadata, and DuckDB's default per column compression
/// takes care of the rest.
public final class Schema {
    public static final int VERSION = 1;

    private Schema() {
    }

    public static void create(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE IF NOT EXISTS meta (
                key VARCHAR PRIMARY KEY,
                value VARCHAR NOT NULL
            )""");
        statement.execute("""
            CREATE TABLE IF NOT EXISTS log_file (
                id BIGINT PRIMARY KEY,
                file_name VARCHAR NOT NULL,
                source_kind VARCHAR NOT NULL,
                source_path VARCHAR NOT NULL,
                entry_path VARCHAR NOT NULL,
                log_date DATE NOT NULL,
                date_source VARCHAR NOT NULL,
                minecraft_version VARCHAR NOT NULL,
                last_modified TIMESTAMP,
                first_entry_time TIMESTAMP,
                last_entry_time TIMESTAMP,
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
        statement.execute("CREATE INDEX IF NOT EXISTS chat_entry_location ON chat_entry (file_id, line_index)");
        statement.execute("INSERT OR IGNORE INTO meta VALUES ('schema_version', '" + VERSION + "')");
    }
}
