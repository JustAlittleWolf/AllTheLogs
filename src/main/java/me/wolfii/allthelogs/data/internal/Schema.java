package me.wolfii.allthelogs.data.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/// The database layout. Callers of the public API never see any of this.
///
/// The design is driven by the two access patterns that matter: scanning a date range and scanning for a text match.
/// Entries are stored in one table clustered by timestamp, which lets DuckDB skip whole row groups via its zone maps
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
    private static final String CLUSTERED_FLAG = "entries_clustered";

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
        statement.execute("""
            CREATE TABLE IF NOT EXISTS allthelogs_meta (
                k VARCHAR PRIMARY KEY,
                v VARCHAR NOT NULL
            )""");
        if (!metaFlag(statement, CLUSTERED_FLAG)) {
            clusterEntries(statement);
            setMetaFlag(statement, CLUSTERED_FLAG);
        }
    }

    /// Rewrites `chat_entry` in timestamp order so row-group zone maps can skip data on range scans and
    /// `ORDER BY entry_time, file_id, line_index` can stream instead of sorting the whole result.
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
        setMetaFlag(statement, CLUSTERED_FLAG);
    }

    private static boolean metaFlag(Statement statement, String key) throws SQLException {
        try (ResultSet result = statement.executeQuery(
            "SELECT 1 FROM allthelogs_meta WHERE k = '" + key + "' AND v = '1'")) {
            return result.next();
        }
    }

    private static void setMetaFlag(Statement statement, String key) throws SQLException {
        statement.execute("DELETE FROM allthelogs_meta WHERE k = '" + key + "'");
        statement.execute("INSERT INTO allthelogs_meta VALUES ('" + key + "', '1')");
    }
}
