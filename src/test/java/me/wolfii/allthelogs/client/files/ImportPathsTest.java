package me.wolfii.allthelogs.client.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportPathsTest {
    @Test
    void recognisesArchiveAndLogNames() {
        assertTrue(ImportPaths.isArchive(Path.of("logs.zip")));
        assertTrue(ImportPaths.isArchive(Path.of("pack.tar.gz")));
        assertTrue(ImportPaths.isArchive(Path.of("old.7z")));
        assertFalse(ImportPaths.isArchive(Path.of("latest.log")));
        assertFalse(ImportPaths.isArchive(Path.of("folder")));
        assertTrue(ImportPaths.isLogFile(Path.of("latest.log")));
        assertTrue(ImportPaths.isLogFile(Path.of("2026-01-02-1.log.gz")));
        assertFalse(ImportPaths.isLogFile(Path.of("backup.zip")));
    }

    @Test
    void droppedDirectoryWinsOverArchives(@TempDir Path root) throws IOException {
        Path folder = Files.createDirectory(root.resolve("instance"));
        Path archive = root.resolve("backup.zip");
        Files.writeString(archive, "x");
        assertEquals(folder.toAbsolutePath().normalize(),
            ImportPaths.fromDropped(List.of(archive, folder)).orElseThrow());
    }

    @Test
    void droppedArchiveIsPreferredOverLooseFiles(@TempDir Path root) throws IOException {
        Path archive = root.resolve("backup.zip");
        Path notes = root.resolve("notes.txt");
        Files.writeString(archive, "x");
        Files.writeString(notes, "no");
        assertEquals(archive.toAbsolutePath().normalize(),
            ImportPaths.fromDropped(List.of(notes, archive)).orElseThrow());
    }

    @Test
    void droppedLogFileSelectsItsParent(@TempDir Path root) throws IOException {
        Path logs = Files.createDirectory(root.resolve("logs"));
        Path log = logs.resolve("latest.log");
        Files.writeString(log, "chat");
        assertEquals(logs.toAbsolutePath().normalize(),
            ImportPaths.fromDropped(List.of(log)).orElseThrow());
    }

    @Test
    void startDirectoryUsesTheParentWhenGivenAFile(@TempDir Path root) throws IOException {
        Path file = root.resolve("a.zip");
        Files.writeString(file, "x");
        assertEquals(root.toAbsolutePath().normalize(), ImportPaths.startDirectory(file));
        assertEquals(root.toAbsolutePath().normalize(), ImportPaths.startDirectory(root));
    }
}
