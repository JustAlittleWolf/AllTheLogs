package me.wolfii.allthelogs.data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds small log files and archives for the tests.
 */
final class LogFixtures {
    private LogFixtures() {
    }

    /** A modern Fabric style log whose first line carries the Minecraft version. */
    static String modernLog(String version, String... chatMessages) {
        StringBuilder log = new StringBuilder();
        log.append("[10:00:00] [main/INFO]: Loading Minecraft ").append(version)
            .append(" with Fabric Loader 0.19.3\n");
        log.append("[10:00:01] [main/INFO]: Loading 65 mods:\n");
        log.append("\t- fabric-api 0.152.1\n");
        int second = 10;
        for (String message : chatMessages) {
            log.append(String.format("[10:00:%02d] [Render thread/INFO]: [CHAT] %s%n", second++, message));
            log.append("[10:00:").append(second).append("] [Chunk Batcher 3/WARN]: Needed to grow BufferBuilder buffer\n");
        }
        return log.toString();
    }

    /** A 1.8.9 Forge style log; the version only shows up in a Forge Mod Loader line. */
    static String legacyLog(String... chatMessages) {
        StringBuilder log = new StringBuilder();
        log.append("[12:00:00] [main/INFO]: Forge Mod Loader version 11.15.1.2318 for Minecraft 1.8.9 loading\n");
        log.append("[12:00:01] [Client thread/INFO]: LWJGL Version: 2.9.4\n");
        int second = 5;
        for (String message : chatMessages) {
            log.append(String.format("[12:00:%02d] [Client thread/INFO]: [CHAT] %s%n", second++, message));
        }
        return log.toString();
    }

    static Path writePlain(Path directory, String name, String content) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    static Path writeGzipped(Path directory, String name, String content) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /** Writes a zip whose entries are the given path to content pairs; entries ending in {@code .gz} are gzipped. */
    static Path writeZip(Path file, java.util.Map<String, String> entries) throws IOException {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                if (entry.getKey().endsWith(".gz")) {
                    var buffer = new java.io.ByteArrayOutputStream();
                    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                        gzip.write(bytes);
                    }
                    bytes = buffer.toByteArray();
                }
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        return file;
    }

    static byte[] zipBytes(java.util.Map<String, String> entries) throws IOException {
        var buffer = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
