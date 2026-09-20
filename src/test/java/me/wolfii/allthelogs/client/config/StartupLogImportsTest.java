package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.data.ImportOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StartupLogImportsTest {
    @TempDir
    Path temp;

    @Test
    void prefersALogsSubfolderWithTheCurrentLogsOptions() throws Exception {
        Path instance = temp.resolve("game");
        Files.createDirectories(instance.resolve("logs"));
        Optional<StartupLogImports.Target> target = StartupLogImports.targetFor(instance);
        assertTrue(target.isPresent());
        assertEquals(instance.resolve("logs"), target.get().root());
        assertEquals(ImportOptions.currentLogsDirectory(), target.get().options());
    }

    @Test
    void fallsBackToTheGameDirectoryGlobWhenThereIsNoLogsFolder() throws Exception {
        Path instance = Files.createDirectories(temp.resolve("bare"));
        Optional<StartupLogImports.Target> target = StartupLogImports.targetFor(instance);
        assertTrue(target.isPresent());
        assertEquals(instance, target.get().root());
        assertEquals(ImportOptions.currentGameDirectory(), target.get().options());
    }

    @Test
    void treatsALogsFolderAsTheCurrentLogsDirectory() throws Exception {
        Path logs = Files.createDirectories(temp.resolve("other-instance").resolve("logs"));
        Files.createFile(logs.resolve("2026-01-01-1.log"));
        Optional<StartupLogImports.Target> target = StartupLogImports.targetFor(logs);
        assertTrue(target.isPresent());
        assertEquals(logs, target.get().root());
        assertEquals(ImportOptions.currentLogsDirectory(), target.get().options());
    }

    @Test
    void skipsMissingDirectories() {
        assertTrue(StartupLogImports.targetFor(temp.resolve("gone")).isEmpty());
        assertTrue(StartupLogImports.targetFor(null).isEmpty());
    }
}
