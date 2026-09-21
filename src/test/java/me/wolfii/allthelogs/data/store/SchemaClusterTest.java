package me.wolfii.allthelogs.data.store;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaClusterTest {
    private static void insertFile(Statement statement, long id, String time) throws SQLException {
        statement.execute("""
            INSERT INTO log_file VALUES (
                %d, 'chat.log', 'FILE', '/tmp/%d.log', '/tmp/%d.log',
                DATE '2026-01-01', '26.2',
                TIMESTAMP '%s', TIMESTAMP '%s', 1, NULL)""".formatted(id, id, id, time, time));
        statement.execute("""
            INSERT INTO chat_entry VALUES (
                %d, 0, TIMESTAMP '%s', 'line %d', NULL, NULL, NULL)""".formatted(id, time, id));
    }

    private static long marker(Statement statement) throws SQLException {
        try (var result = statement.executeQuery(
            "SELECT v FROM allthelogs_meta WHERE k = 'clustered_before_file_id'")) {
            result.next();
            return Long.parseLong(result.getString(1));
        }
    }

    @Test
    void firstClusterDoesNotAskForCompact() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            insertFile(statement, 1, "2026-08-01 10:00:00");
            insertFile(statement, 2, "2026-01-01 10:00:00");

            assertFalse(Schema.clusterAfterImport(statement, null));
            assertEquals(3, marker(statement));
            try (var result = statement.executeQuery(
                "SELECT message FROM chat_entry ORDER BY entry_time, file_id, line_index")) {
                result.next();
                assertEquals("line 2", result.getString(1));
                result.next();
                assertEquals("line 1", result.getString(1));
            }
        }
    }

    @Test
    void newerFilesUseTailRewriteWithoutCompact() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            insertFile(statement, 1, "2026-01-01 10:00:00");
            Schema.clusterEntries(statement);
            insertFile(statement, 2, "2026-08-01 10:00:00");

            assertFalse(Schema.clusterAfterImport(statement, null));
            assertEquals(3, marker(statement));
        }
    }

    @Test
    void interleavedOlderFilesRewriteAndAskForCompact() throws SQLException {
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            insertFile(statement, 1, "2026-08-01 10:00:00");
            Schema.clusterEntries(statement);
            insertFile(statement, 2, "2026-01-01 10:00:00");

            assertTrue(Schema.clusterAfterImport(statement, null));
            try (var result = statement.executeQuery(
                "SELECT message FROM chat_entry ORDER BY entry_time, file_id, line_index")) {
                result.next();
                assertEquals("line 2", result.getString(1));
                result.next();
                assertEquals("line 1", result.getString(1));
            }
        }
    }
}
