package me.wolfii.allthelogs.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtraImportDirectoriesTest {
    @TempDir
    Path temp;

    @Test
    void persistedListOmitsTheInstanceDirectoryAndItsLogsFolder() {
        Path instance = temp.resolve("instance").toAbsolutePath().normalize();
        List<String> extra = ExtraImportDirectories.persisted(List.of(
            instance.toString(),
            instance.resolve("logs").toString(),
            temp.resolve("other").toString(),
            temp.resolve("other").toString(),
            "  ",
            null
        ), instance);
        assertEquals(List.of(temp.resolve("other").toAbsolutePath().normalize().toString()), extra);
    }

    @Test
    void displayRootsLeadWithTheInstanceDirectory() {
        Path instance = temp.resolve("game");
        Path extra = temp.resolve("pack");
        List<Path> display = ExtraImportDirectories.displayRoots(instance,
            List.of(extra.toString(), instance.toString()));
        assertEquals(instance, display.getFirst());
        assertEquals(2, display.size());
        assertEquals(extra.toAbsolutePath().normalize(), display.get(1));
    }

    @Test
    void scanRootsNeverIncludeTheInstance() {
        Path instance = temp.resolve("game");
        assertTrue(ExtraImportDirectories.scanRoots(List.of(instance.toString()), instance).isEmpty());
        assertTrue(ExtraImportDirectories.isInstanceDirectory(instance, instance));
        assertTrue(ExtraImportDirectories.isInstanceDirectory(instance.resolve("logs"), instance));
        assertFalse(ExtraImportDirectories.isInstanceDirectory(temp.resolve("other"), instance));
    }
}
