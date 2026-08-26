package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void freshDatabaseIsInitializedAtCurrentVersion() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            assertTrue(tableExists(statement, "log_file"));
            assertTrue(tableExists(statement, "chat_entry"));
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
    void legacyDatabaseWithoutVersionIsAdoptedAtCurrentVersion() throws SQLException {
        Path database = tempDir.resolve("legacy.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            Schema.create(statement);
            statement.execute("DROP TABLE IF EXISTS " + SchemaMigration.META_TABLE);
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            assertTrue(tableExists(statement, "log_file"));
            assertTrue(tableExists(statement, "chat_entry"));
        }
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

        LogDataException error = assertThrows(LogDataException.class, () -> LogStore.open(database));
        SQLException cause = assertInstanceOf(SQLException.class, error.getCause());
        assertTrue(cause.getMessage().contains("newer than this mod supports"));
    }

    private static boolean tableExists(Statement statement, String tableName) throws SQLException {
        try (var result = statement.executeQuery("""
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'main' AND table_name = '""" + tableName + "'")) {
            result.next();
            return result.getLong(1) > 0;
        }
    }
}
