package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMigrationTest {
    @TempDir
    Path tempDir;

    private static String meta(Statement statement, String key) throws SQLException {
        try (ResultSet result = statement.executeQuery(
            "SELECT v FROM " + SchemaMigration.META_TABLE + " WHERE k = '" + key + "'")) {
            assertTrue(result.next());
            return result.getString(1);
        }
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
    void legacyImportedEntriesSurviveVersionAdoption() throws SQLException {
        Path database = tempDir.resolve("legacy-data.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO log_file VALUES (
                    1, '2026-08-24-1.log.gz', 'FILE', '/tmp/legacy.log', '/tmp/legacy.log',
                    DATE '2026-08-24', '26.2',
                    TIMESTAMP '2026-08-24 10:00:00', TIMESTAMP '2026-08-24 10:00:10', 1, NULL)""");
            statement.execute("""
                INSERT INTO chat_entry VALUES (
                    1, 0, TIMESTAMP '2026-08-24 10:00:10', 'hello from legacy', NULL)""");
            statement.execute("DROP TABLE IF EXISTS " + SchemaMigration.META_TABLE);
        }

        try (LogStore store = LogStore.open(database)) {
            List<String> messages = store.allEntries().stream().map(ChatEntry::message).toList();
            assertEquals(List.of("hello from legacy"), messages);
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
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

    @Test
    void upgradeAppliesEachStepAndAdvancesTheStoredVersion() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            assertEquals(1, SchemaMigration.readVersion(statement));

            SchemaMigration.upgrade(statement, 1, 3, from -> stmt ->
                stmt.execute("INSERT INTO " + SchemaMigration.META_TABLE
                    + " VALUES ('from_" + from + "', 'ok')"));

            assertEquals(3, SchemaMigration.readVersion(statement));
            assertEquals("ok", meta(statement, "from_1"));
            assertEquals("ok", meta(statement, "from_2"));
        }
    }

    @Test
    void upgradeDoesNotAdvanceVersionWhenAStepIsMissing() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            SQLException error = assertThrows(SQLException.class,
                () -> SchemaMigration.upgrade(statement, 1, 2, from -> null));
            assertTrue(error.getMessage().contains("no migration path from schema version 1"));
            assertEquals(1, SchemaMigration.readVersion(statement));
        }
    }

    @Test
    void upgradeDoesNotAdvanceVersionWhenAStepFails() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            SQLException error = assertThrows(SQLException.class, () ->
                SchemaMigration.upgrade(statement, 1, 2, from -> stmt -> {
                    throw new SQLException("boom");
                }));
            assertEquals("boom", error.getMessage());
            assertEquals(1, SchemaMigration.readVersion(statement));
        }
    }
}
