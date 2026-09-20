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
    void persistedListStoresLogsFoldersAndOmitsTheCurrentInstance() {
        Path instance = temp.resolve("instance").toAbsolutePath().normalize();
        java.util.ArrayList<String> configured = new java.util.ArrayList<>();
        configured.add(instance.toString());
        configured.add(instance.resolve("logs").toString());
        configured.add(temp.resolve("other").toString());
        configured.add(temp.resolve("other").resolve("logs").toString());
        configured.add("  ");
        configured.add(null);
        List<String> extra = ExtraImportDirectories.persisted(configured, instance);
        assertEquals(List.of(temp.resolve("other").resolve("logs").toAbsolutePath().normalize().toString()), extra);
    }

    @Test
    void displayRootsLeadWithTheInstanceDirectory() {
        Path instance = temp.resolve("game");
        Path extra = temp.resolve("pack");
        List<Path> display = ExtraImportDirectories.displayRoots(instance,
            List.of(extra.toString(), instance.toString()));
        assertEquals(instance, display.getFirst());
        assertEquals(2, display.size());
        assertEquals(extra.resolve("logs").toAbsolutePath().normalize(), display.get(1));
    }

    @Test
    void scanRootsNeverIncludeTheInstance() {
        Path instance = temp.resolve("game");
        assertTrue(ExtraImportDirectories.scanRoots(List.of(instance.toString()), instance).isEmpty());
        assertTrue(ExtraImportDirectories.isInstanceDirectory(instance, instance));
        assertTrue(ExtraImportDirectories.isInstanceDirectory(instance.resolve("logs"), instance));
        assertFalse(ExtraImportDirectories.isInstanceDirectory(temp.resolve("other"), instance));
    }

    @Test
    void toLogsFolderKeepsALogsDirectoryAndAppendsOtherwise() {
        Path logs = temp.resolve("pack").resolve("logs");
        assertEquals(logs.toAbsolutePath().normalize(), ExtraImportDirectories.toLogsFolder(logs));
        assertEquals(logs.toAbsolutePath().normalize(), ExtraImportDirectories.toLogsFolder(temp.resolve("pack")));
    }
}
