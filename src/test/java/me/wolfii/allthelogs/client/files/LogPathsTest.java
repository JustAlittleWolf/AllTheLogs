package me.wolfii.allthelogs.client.files;

import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogPathsTest {
    @Test
    void fileLogOpensItsOwnPath() {
        Path log = Path.of("/tmp/instance/logs/latest.log");
        assertEquals(log.toAbsolutePath().normalize(), LogPaths.openable(new LogSource.File(log)));
    }

    @Test
    void filePathThatContinuesPastAZipStopsAtTheZip() {
        Path inside = Path.of("/tmp/backups/world.zip/logs/latest.log");
        assertEquals(Path.of("/tmp/backups/world.zip").toAbsolutePath().normalize(),
            LogPaths.openable(new LogSource.File(inside)));
        Path bang = Path.of("/tmp/backups/world.zip!/logs/latest.log");
        assertEquals(Path.of("/tmp/backups/world.zip").toAbsolutePath().normalize(),
            LogPaths.openable(new LogSource.File(bang)));
    }

    @Test
    void archiveLogOpensTheArchiveRatherThanTheEntry() {
        Path archive = Path.of("/tmp/instance/backups/world.zip");
        LogSource source = new LogSource.Archive(archive, "logs/latest.log");
        assertEquals(archive.toAbsolutePath().normalize(), LogPaths.openable(source));
    }

    @Test
    void nestedArchiveEntryStillOpensTheOuterArchive() {
        Path archive = Path.of("/tmp/exports/bundle.7z");
        LogSource source = new LogSource.Archive(archive, "instance.zip!/logs/latest.log");
        assertEquals(archive.toAbsolutePath().normalize(), LogPaths.openable(source));
    }

    @Test
    void sessionHasNoPath() {
        assertNull(LogPaths.openable(new LogSource.Session("abc")));
        assertNull(LogPaths.openable(null));
    }

    @Test
    void pathStopsAtTheFirstArchiveSegment() {
        assertEquals("/data/backup.zip", LogPaths.throughArchive("/data/backup.zip/logs/latest.log"));
        assertEquals("/data/backup.zip", LogPaths.throughArchive("/data/backup.zip!/logs/latest.log"));
        assertEquals("/data/outer.zip", LogPaths.throughArchive("/data/outer.zip/inner.zip/logs/latest.log"));
        assertEquals("C:\\mods\\pack.tar.gz", LogPaths.throughArchive("C:\\mods\\pack.tar.gz\\logs\\latest.log"));
        assertEquals("C:\\backups\\world.zip", LogPaths.throughArchive("C:\\backups\\world.zip!\\logs\\latest.log"));
        assertEquals("C:\\logs\\read!\\this.log", LogPaths.throughArchive("C:\\logs\\read!\\this.log"));
        assertEquals("/logs/2026-01-02-1.log.gz", LogPaths.throughArchive("/logs/2026-01-02-1.log.gz"));
        assertEquals("/logs/read!/this.log", LogPaths.throughArchive("/logs/read!/this.log"));
        assertNull(LogPaths.throughArchive(null));
        assertNull(LogPaths.throughArchive("  "));
    }
}
