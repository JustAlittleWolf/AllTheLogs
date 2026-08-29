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
            assertTrue(tableExists(statement, "import_seen"));
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
            assertTrue(tableExists(statement, "import_seen"));
        }
    }

    @Test
    void version1DatabaseGainsImportSeenOnOpen() throws SQLException {
        Path database = tempDir.resolve("v1.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS import_seen");
            statement.execute("DELETE FROM " + SchemaMigration.META_TABLE
                + " WHERE k = '" + SchemaMigration.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + SchemaMigration.META_TABLE + " VALUES ('"
                + SchemaMigration.VERSION_KEY + "', '1')");
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            assertTrue(tableExists(statement, "import_seen"));
            assertTrue(columnExists(statement, "import_seen", "content_hash"));
        }
    }

    @Test
    void version2DatabaseGainsContentHashOnOpen() throws SQLException {
        Path database = tempDir.resolve("v2.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS import_seen_hash");
            statement.execute("ALTER TABLE import_seen DROP COLUMN content_hash");
            statement.execute("DELETE FROM " + SchemaMigration.META_TABLE
                + " WHERE k = '" + SchemaMigration.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + SchemaMigration.META_TABLE + " VALUES ('"
                + SchemaMigration.VERSION_KEY + "', '2')");
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(SchemaMigration.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            assertTrue(columnExists(statement, "import_seen", "content_hash"));

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
            int start = SchemaMigration.CURRENT_VERSION;
            assertEquals(start, SchemaMigration.readVersion(statement));

            SchemaMigration.upgrade(statement, start, start + 2, from -> stmt ->
                stmt.execute("INSERT INTO " + SchemaMigration.META_TABLE
                    + " VALUES ('from_" + from + "', 'ok')"));

            assertEquals(start + 2, SchemaMigration.readVersion(statement));
            assertEquals("ok", meta(statement, "from_" + start));
            assertEquals("ok", meta(statement, "from_" + (start + 1)));
        }
    }

    @Test
    void upgradeDoesNotAdvanceVersionWhenAStepIsMissing() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            int start = SchemaMigration.CURRENT_VERSION;
            SQLException error = assertThrows(SQLException.class,
                () -> SchemaMigration.upgrade(statement, start, start + 1, from -> null));
            assertTrue(error.getMessage().contains("no migration path from schema version " + start));
            assertEquals(start, SchemaMigration.readVersion(statement));
        }
    }

    @Test
    void upgradeDoesNotAdvanceVersionWhenAStepFails() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            int start = SchemaMigration.CURRENT_VERSION;
            SQLException error = assertThrows(SQLException.class, () ->
                SchemaMigration.upgrade(statement, start, start + 1, from -> stmt -> {
                    throw new SQLException("boom");
                }));
            assertEquals("boom", error.getMessage());
            assertEquals(start, SchemaMigration.readVersion(statement));
        }
    }
}
