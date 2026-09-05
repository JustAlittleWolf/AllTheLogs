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
    public static final int CURRENT_VERSION = 4;
    static final String META_TABLE = "allthelogs_meta";
    static final String VERSION_KEY = "schema_version";
    static final String CLUSTER_MARKER_KEY = "clustered_before_file_id";

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
        statement.execute("""
            CREATE TABLE IF NOT EXISTS allthelogs_meta (
                k VARCHAR PRIMARY KEY,
                v VARCHAR NOT NULL
            )""");
        statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('" + Schema.CLUSTER_MARKER_KEY + "', '0')");
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
            advanceClusterMarker(statement);
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
        advanceClusterMarker(statement);
    }

    /**
     * Rewrites just the {@code chat_entry} rows appended since the last {@link #clusterEntries} or
     * {@link #clusterTail} pass — those with {@code file_id} at or after the stored cluster marker — sorted
     * the same way as {@link #clusterEntries}, then appends them back after the rest of the table.
     * <p>
     * Live session capture inserts one row per chat line as it happens, so a session's rows always carry
     * the newest {@code entry_time} in the store. That means this never has to touch the already-sorted,
     * already-clustered rows before the marker — it only needs to re-pack whatever landed one row at a
     * time since the last catch-up into fewer, larger row groups, which is cheap regardless of how large
     * the historical log history is.
     * <p>
     * This is <b>not</b> a substitute for {@link #clusterEntries}: it assumes the tail is chronologically
     * last, which holds for live session capture but not for an arbitrary file/archive import (a user can
     * import an old backup after already having recent data). Call this before starting a new session, not
     * after an import — imports still go through the full {@link #clusterEntries} rewrite.
     */
    public static void clusterTail(Statement statement) throws SQLException {
        long marker = readClusterMarker(statement);
        long pending;
        try (ResultSet result = statement.executeQuery(
            "SELECT count(*) FROM chat_entry WHERE file_id >= " + marker)) {
            result.next();
            pending = result.getLong(1);
        }
        if (pending > 0) {
            statement.execute("DROP TABLE IF EXISTS chat_entry_tail");
            statement.execute("""
                CREATE TEMP TABLE chat_entry_tail AS
                SELECT file_id, line_index, entry_time, message, formatting
                FROM chat_entry
                WHERE file_id >= %d
                ORDER BY entry_time, file_id, line_index""".formatted(marker));
            statement.execute("DELETE FROM chat_entry WHERE file_id >= " + marker);
            statement.execute("INSERT INTO chat_entry SELECT * FROM chat_entry_tail");
            statement.execute("DROP TABLE chat_entry_tail");
        }
        advanceClusterMarker(statement);
    }

    private static void advanceClusterMarker(Statement statement) throws SQLException {
        long nextFileId;
        try (ResultSet result = statement.executeQuery("SELECT coalesce(max(id) + 1, 0) FROM log_file")) {
            result.next();
            nextFileId = result.getLong(1);
        }
        statement.execute(
            "DELETE FROM " + Schema.META_TABLE + " WHERE k = '" + CLUSTER_MARKER_KEY + "'");
        statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('" + CLUSTER_MARKER_KEY + "', '"
            + nextFileId + "')");
    }

    private static long readClusterMarker(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(
            "SELECT v FROM " + Schema.META_TABLE + " WHERE k = '" + CLUSTER_MARKER_KEY + "'")) {
            if (!result.next()) {
                return 0;
            }
            return Long.parseLong(result.getString(1));
        }
    }
}
