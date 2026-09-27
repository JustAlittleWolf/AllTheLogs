package me.wolfii.allthelogs.data.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseBackupTest {
    @TempDir
    Path tempDir;

    @Test
    void createWritesAGzipCopyOfTheDatabaseBytes() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        Files.writeString(database, "duckdb-bytes");
        Instant at = Instant.parse("2026-09-22T13:15:00Z");

        Path backup = DatabaseBackup.create(database, 4, at);

        assertEquals("logs.duckdb.v4.20260922T131500Z.gz", backup.getFileName().toString());
        assertEquals("duckdb-bytes", new String(decompress(backup)));
        assertFalse(Files.exists(backup.resolveSibling(backup.getFileName() + ".tmp")));
    }

    @Test
    void pruneKeepsTheNewestBackupEvenWhenItIsOlderThanThreeMonths() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        Files.writeString(database, "db");
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        Path newest = touchBackup(database, 4, Instant.parse("2026-01-01T00:00:00Z"));
        Path stale = touchBackup(database, 3, Instant.parse("2025-01-01T00:00:00Z"));
        Path alsoStale = touchBackup(database, 3, Instant.parse("2025-06-01T00:00:00Z"));

        DatabaseBackup.pruneExpired(database, now);

        assertTrue(Files.exists(newest), "newest backup must survive even past three months");
        assertFalse(Files.exists(stale));
        assertFalse(Files.exists(alsoStale));
    }

    @Test
    void pruneKeepsBackupsYoungerThanThreeMonths() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        Files.writeString(database, "db");
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        Path recent = touchBackup(database, 5, Instant.parse("2026-08-01T00:00:00Z"));
        Path alsoRecent = touchBackup(database, 4, Instant.parse("2026-07-01T00:00:00Z"));
        Path stale = touchBackup(database, 3, Instant.parse("2025-01-01T00:00:00Z"));

        DatabaseBackup.pruneExpired(database, now);

        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(alsoRecent));
        assertFalse(Files.exists(stale));
    }

    @Test
    void pruneDoesNothingWhenOnlyOneBackupExists() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        Files.writeString(database, "db");
        Path only = touchBackup(database, 4, Instant.parse("2020-01-01T00:00:00Z"));

        DatabaseBackup.pruneExpired(database, Instant.parse("2026-09-22T12:00:00Z"));

        assertTrue(Files.exists(only));
    }

    @Test
    void openingAnOlderDatabaseCopiesTheFileThenMigrates() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE chat_entry DROP COLUMN IF EXISTS server_or_world");
            statement.execute("ALTER TABLE chat_entry DROP COLUMN IF EXISTS minecraft_user");
            statement.execute("DELETE FROM " + Schema.META_TABLE
                + " WHERE k = '" + Schema.VERSION_KEY + "'");
            statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('"
                + Schema.VERSION_KEY + "', '4')");
        }

        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            assertEquals(Schema.CURRENT_VERSION, SchemaMigration.readVersion(statement));
            try (ResultSet result = statement.executeQuery(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'chat_entry' "
                    + "AND column_name = 'server_or_world'")) {
                result.next();
                assertEquals(1, result.getLong(1));
            }
        }

        List<DatabaseBackup.Copy> backups = DatabaseBackup.list(database);
        assertEquals(1, backups.size());
        assertTrue(backups.getFirst().path().getFileName().toString().contains(".v4."));
        assertTrue(decompress(backups.getFirst().path()).length > 0);
    }

    @Test
    void listAndPruneStillSeeLegacyLz4Backups() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        Files.writeString(database, "db");
        Path legacy = database.resolveSibling("logs.duckdb.v4.20260101T000000Z.lz4");
        Files.writeString(legacy, "old");
        Path newer = DatabaseBackup.backupPath(database, 5, Instant.parse("2026-09-01T00:00:00Z"));
        Files.writeString(newer, "new");

        List<DatabaseBackup.Copy> listed = DatabaseBackup.list(database);
        assertEquals(2, listed.size());
        assertEquals(legacy, listed.getFirst().path());

        DatabaseBackup.pruneExpired(database, Instant.parse("2026-09-22T12:00:00Z"));

        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(newer));
    }

    @Test
    void reopeningTheCurrentSchemaDoesNotWriteABackup() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        try (var ignored = StoreConnections.openFile(database)) {
        }
        try (var ignored = StoreConnections.openFile(database)) {
        }
        assertTrue(DatabaseBackup.list(database).isEmpty());
    }

    private static Path touchBackup(Path database, int version, Instant at) throws Exception {
        Path path = DatabaseBackup.backupPath(database, version, at);
        Files.writeString(path, "backup");
        return path;
    }

    private static byte[] decompress(Path backup) throws Exception {
        try (InputStream file = Files.newInputStream(backup);
             GZIPInputStream gzip = new GZIPInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gzip.transferTo(out);
            return out.toByteArray();
        }
    }
}
