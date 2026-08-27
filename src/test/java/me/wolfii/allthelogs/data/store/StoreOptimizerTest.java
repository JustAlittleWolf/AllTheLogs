package me.wolfii.allthelogs.data.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreOptimizerTest {
    @TempDir
    Path tempDir;

    @Test
    void compactCopyReclaimsSpaceLeftByAClusterRewrite() throws SQLException, IOException {
        Path database = tempDir.resolve("logs.duckdb");
        long afterRewrite;
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE chat_payload (message VARCHAR)");
            statement.execute("INSERT INTO chat_payload SELECT 'xxxxxxxxxxxxxxxx' || i FROM range(80_000) t(i)");
            statement.execute("CHECKPOINT");
            long packed = Files.size(database);
            statement.execute("""
                CREATE TABLE chat_payload_sorted AS
                SELECT * FROM chat_payload ORDER BY message""");
            statement.execute("DROP TABLE chat_payload");
            statement.execute("ALTER TABLE chat_payload_sorted RENAME TO chat_payload");
            statement.execute("CHECKPOINT");
            afterRewrite = Files.size(database);
            assertTrue(afterRewrite >= packed,
                "cluster rewrite should not shrink below the live table; packed="
                    + packed + " rewritten=" + afterRewrite);
        }

        try (var connection = StoreConnections.openFile(database)) {
            var compacted = StoreOptimizer.replaceWithCompactCopy(connection, database);
            compacted.close();
        }

        long compactedSize = Files.size(database);
        assertTrue(compactedSize < afterRewrite,
            "compacted " + compactedSize + " should be under rewritten " + afterRewrite);
        assertTrue(Files.notExists(StoreOptimizer.walPath(database))
            || Files.size(StoreOptimizer.walPath(database)) == 0);
    }

    @Test
    void compactCopyKeepsTablesAndIndexes() throws SQLException, IOException {
        Path database = tempDir.resolve("keep.duckdb");
        var connection = StoreConnections.openFile(database);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO log_file VALUES (
                    1, 'chat.log', 'FILE', '/tmp/chat.log', '/tmp/chat.log',
                    DATE '2026-08-24', '26.2',
                    TIMESTAMP '2026-08-24 10:00:00', TIMESTAMP '2026-08-24 10:00:10', 1, NULL)""");
            statement.execute("""
                INSERT INTO chat_entry VALUES (
                    1, 0, TIMESTAMP '2026-08-24 10:00:10', 'hello', NULL)""");
        }
        var compacted = StoreOptimizer.replaceWithCompactCopy(connection, database);
        try (Statement check = compacted.createStatement()) {
            try (ResultSet entries = check.executeQuery("SELECT message FROM chat_entry")) {
                assertTrue(entries.next());
                assertEquals("hello", entries.getString(1));
            }
            try (ResultSet files = check.executeQuery("SELECT file_name FROM log_file")) {
                assertTrue(files.next());
                assertEquals("chat.log", files.getString(1));
            }
            try (ResultSet indexes = check.executeQuery("""
                SELECT index_name FROM duckdb_indexes()
                WHERE table_name = 'log_file' AND index_name = 'log_file_location'""")) {
                assertTrue(indexes.next());
            }
        }
        compacted.close();
    }
}
