package me.wolfii.allthelogs.client.files;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRevealTest {
    @Test
    void windowsFallbackOpensTheContainingFolder() {
        assertEquals("C:\\logs\\My Logs", FileReveal.windowsDirectory("C:\\logs\\My Logs\\latest.log"));
        assertEquals("C:\\", FileReveal.windowsDirectory("C:\\latest.log"));
        assertEquals(List.of("explorer.exe", "C:\\logs\\My Logs"),
            FileReveal.windowsOpenFolder(Path.of("C:\\logs\\My Logs\\latest.log")));
    }

    @Test
    void macFinderRevealsTheFile() {
        Path file = Path.of("/tmp/logs/latest.log");
        assertEquals(List.of("open", "-R", file.toString()), FileReveal.macSelect(file));
    }

    @Test
    void linuxSelectsTheFileUri() {
        Path file = Path.of("/tmp/logs/my log.log");
        List<String> command = FileReveal.linuxSelect(file);
        assertEquals("dbus-send", command.getFirst());
        assertTrue(command.stream().anyMatch(arg -> arg.contains("org.freedesktop.FileManager1.ShowItems")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains(file.toUri().toString())));
    }

    @Test
    void linuxFallbackOpensTheContainingFolder() {
        Path log = Path.of("/tmp/logs/latest.log");
        assertEquals(List.of("xdg-open", log.getParent().toString()), FileReveal.linuxFolder(log));
        Path archive = Path.of("/tmp/backups/world.zip");
        assertEquals(List.of("xdg-open", archive.getParent().toString()), FileReveal.linuxFolder(archive));
    }
}
