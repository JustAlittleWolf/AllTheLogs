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
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
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
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
        }
    }

    @Test
    void unversionedDatabaseIsRejected() throws SQLException {
        Path database = tempDir.resolve("legacy.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + SchemaMigration.META_TABLE);
        }

        SQLException cause = openRejected(database);
        assertTrue(cause.getMessage().contains("does not migrate"));
    }

    @Test
    void olderDatabaseVersionIsRejected() throws SQLException {
        Path database = tempDir.resolve("v1.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + SchemaMigration.META_TABLE
                + " WHERE k = '" + SchemaMigration.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + SchemaMigration.META_TABLE + " VALUES ('"
                + SchemaMigration.VERSION_KEY + "', '1')");
        }

        SQLException cause = openRejected(database);
        assertTrue(cause.getMessage().contains("older than this mod"));
        assertTrue(cause.getMessage().contains("does not migrate"));
    }

    @Test
    void newerDatabaseVersionIsRejected() throws SQLException {
        Path database = tempDir.resolve("future.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + SchemaMigration.META_TABLE
                + " WHERE k = '" + SchemaMigration.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + SchemaMigration.META_TABLE + " VALUES ('"
                + SchemaMigration.VERSION_KEY + "', '999')");
        }

        SQLException cause = openRejected(database);
        assertTrue(cause.getMessage().contains("newer than this mod supports"));
    }
}
