package me.wolfii.allthelogs.client.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBrowserListingTest {
    @Test
    void recognisesArchiveExtensions() {
        assertTrue(FileBrowserListing.isArchive(Path.of("logs.zip")));
        assertTrue(FileBrowserListing.isArchive(Path.of("pack.tar.gz")));
        assertTrue(FileBrowserListing.isArchive(Path.of("old.7z")));
        assertFalse(FileBrowserListing.isArchive(Path.of("latest.log")));
        assertFalse(FileBrowserListing.isArchive(Path.of("folder")));
    }

    @Test
    void listsDirectoriesAndArchivesWithoutHiddenFiles(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("logs"));
        Files.writeString(root.resolve("chat.zip"), "x");
        Files.writeString(root.resolve("notes.txt"), "no");
        Files.writeString(root.resolve(".hidden"), "no");

        List<String> folders = FileBrowserListing.children(root, false).stream()
            .map(path -> path.getFileName().toString())
            .toList();
        assertEquals(List.of("logs"), folders);

        List<String> archives = FileBrowserListing.children(root, true).stream()
            .map(path -> path.getFileName().toString())
            .toList();
        assertEquals(List.of("logs", "chat.zip"), archives);
    }

    @Test
    void startDirectoryUsesTheParentWhenGivenAFile(@TempDir Path root) throws IOException {
        Path file = root.resolve("a.zip");
        Files.writeString(file, "x");
        assertEquals(root.toAbsolutePath().normalize(), FileBrowserListing.startDirectory(file));
        assertEquals(root.toAbsolutePath().normalize(), FileBrowserListing.startDirectory(root));
    }
}
