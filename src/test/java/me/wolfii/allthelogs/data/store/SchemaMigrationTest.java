package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMigrationTest {
    @TempDir
    Path tempDir;

    private static boolean tableExists(Statement statement, String tableName) throws SQLException {
        try (var result = statement.executeQuery("""
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'main' AND table_name = '""" + tableName + "'")) {
            result.next();
            return result.getLong(1) > 0;
        }
    }

    private static boolean columnExists(Statement statement, String tableName, String columnName)
        throws SQLException {
        try (var result = statement.executeQuery("""
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = 'main' AND table_name = '""" + tableName
            + "' AND column_name = '" + columnName + "'")) {
            result.next();
            return result.getLong(1) > 0;
        }
    }

    private static SQLException openRejected(Path database) {
        LogDataException error = assertThrows(LogDataException.class, () -> LogStore.open(database));
        return assertInstanceOf(SQLException.class, error.getCause());
    }

    @Test
    void freshDatabaseIsInitializedAtCurrentVersion() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            assertEquals(Schema.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            assertTrue(tableExists(statement, "log_file"));
            assertTrue(tableExists(statement, "chat_entry"));
            assertTrue(tableExists(statement, "import_seen"));
            assertTrue(columnExists(statement, "import_seen", "content_hash"));
        }
    }

    @Test
    void reopeningDatabaseKeepsCurrentVersion() throws SQLException {
        Path database = tempDir.resolve("logs.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Schema.CURRENT_VERSION, SchemaMigration.readVersion(statement));
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Schema.CURRENT_VERSION, SchemaMigration.readVersion(statement));
        }
    }

    @Test
    void olderDatabaseVersionIsRejected() throws SQLException {
        Path database = tempDir.resolve("v1.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('"
                + Schema.VERSION_KEY + "', '-1')");
        }

        SQLException cause = openRejected(database);
        assertTrue(cause.getMessage().contains("too old to migrate"));
    }

    @Test
    void migratesForwardFromOldestSupportedVersionAndSweepsUpExistingSessionData() throws SQLException {
        Path database = tempDir.resolve("v3.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            // A pre-existing, never-clustered live session, exactly what a real version-3 database
            // (from before Schema#clusterTail existed) would still be carrying around.
            statement.execute("""
                INSERT INTO log_file
                    (id, file_name, source_kind, source_path, entry_path, log_date,
                     minecraft_version, start_time, end_time, entry_count, minecraft_user)
                VALUES (0, 'session', 'SESSION', '<session>', 'session-0', '2024-01-01',
                    '1.20', '2024-01-01 00:00:00', '2024-01-01 00:01:00', 2, NULL)""");
            statement.execute("""
                INSERT INTO chat_entry (file_id, line_index, entry_time, message, formatting) VALUES
                    (0, 0, '2024-01-01 00:00:00', 'hello', NULL),
                    (0, 1, '2024-01-01 00:01:00', 'world', NULL)""");

            // Simulate a real version-3 database: no cluster marker key yet.
            statement.execute("DELETE FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.CLUSTER_MARKER_KEY + "'");
            statement.execute("DELETE FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('"
                + Schema.VERSION_KEY + "', '3')");
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Schema.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            try (ResultSet result = statement.executeQuery("SELECT v FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.CLUSTER_MARKER_KEY + "'")) {
                assertTrue(result.next(), "migration should have seeded the cluster marker");
                assertEquals("0", result.getString(1),
                    "marker starts at 0 so the whole table -- including the pre-existing session -- is pending");
            }

            // The next clusterTail catch-up (normally fired at the next session start) should now treat
            // the entire table as pending, sweeping up the pre-existing session along with everything else.
            Schema.clusterTail(statement);
            try (ResultSet result = statement.executeQuery("SELECT v FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.CLUSTER_MARKER_KEY + "'")) {
                assertTrue(result.next());
                assertEquals("1", result.getString(1), "marker should advance past the swept-up session");
            }
            try (ResultSet result = statement.executeQuery(
                "SELECT count(*) FROM chat_entry WHERE message IN ('hello', 'world')")) {
                assertTrue(result.next());
                assertEquals(2, result.getLong(1), "the pre-existing session's rows must survive the sweep");
            }
        }
    }

    @Test
    void newerDatabaseVersionIsRejected() throws SQLException {
        Path database = tempDir.resolve("future.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('"
                + Schema.VERSION_KEY + "', '999')");
        }

        SQLException cause = openRejected(database);
        assertTrue(cause.getMessage().contains("newer than this mod supports"));
    }
}
