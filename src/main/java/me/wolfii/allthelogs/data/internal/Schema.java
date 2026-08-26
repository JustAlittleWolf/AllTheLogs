package me.wolfii.allthelogs.data.internal;

import java.sql.SQLException;
import java.sql.Statement;

/// The database layout. Callers of the public API never see any of this.
///
/// The design is driven by the two access patterns that matter: scanning a date range and scanning for a text match.
/// Entries are stored in one wide table sorted by timestamp, which lets DuckDB skip whole row groups via its zone maps
/// for range queries and keeps text scans a single sequential pass. Per row overhead is minimised by referencing the
/// file through a small integer id instead of repeating its metadata, and DuckDB's per column compression takes care
/// of the rest.
///
/// `chat_entry` deliberately carries no index. Both supported access patterns are full scans that an index cannot
/// serve, and an index on a table with one row per chat line costs more storage than the compressed data itself.
/// Worse, DuckDB refuses to reuse the blocks of a table that has any ART index, so re-importing a log file would grow
/// the file forever instead of reclaiming what the replaced rows freed. `log_file` keeps its key constraints because
/// it holds one row per file rather than per line, so the same overhead is negligible there.
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
                first_entry_time TIMESTAMP NOT NULL,
                last_entry_time TIMESTAMP NOT NULL,
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
}
