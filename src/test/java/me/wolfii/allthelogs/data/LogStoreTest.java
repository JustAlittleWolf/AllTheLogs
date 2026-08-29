package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.api.ChatQuery.Sort;
import me.wolfii.allthelogs.data.parse.LogDates;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import me.wolfii.allthelogs.data.store.SessionMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LogStoreTest {
    @TempDir
    Path tempDir;

    private LogStore store;

    private static long onDiskSize(Path database) throws IOException {
        long size = Files.size(database);
        Path wal = database.resolveSibling(database.getFileName().toString() + ".wal");
        return Files.isRegularFile(wal) ? size + Files.size(wal) : size;
    }

    private static String taggedLog(String sessionId, String chatTime, String... messages) {
        StringBuilder log = new StringBuilder();
        log.append("[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");
        log.append("[10:00:02] [allthelogs-store/INFO]: ").append(SessionMarker.message(sessionId)).append('\n');
        for (String message : messages) {
            log.append(String.format("[%s] [Render thread/INFO]: [CHAT] %s%n", chatTime, message));
        }
        return log.toString();
    }

    private static String chatLog(String version, String[] times, String[] messages) {
        StringBuilder log = new StringBuilder();
        log.append("[10:00:00] [main/INFO]: Loading Minecraft ").append(version)
            .append(" with Fabric Loader 0.19.3\n");
        for (int i = 0; i < messages.length; i++) {
            log.append(String.format("[%s] [Render thread/INFO]: [CHAT] %s%n", times[i], messages[i]));
        }
        return log.toString();
    }

    private static String fileName(ChatLog log) {
        return switch (log.source()) {
            case LogSource.File file -> file.path().getFileName().toString();
            case LogSource.Archive archive -> {
                String entry = archive.entryPath();
                yield entry.substring(entry.lastIndexOf('/') + 1);
            }
            case LogSource.Session session -> throw new AssertionError("session has no file name");
        };
    }

    private static void replaceLogFilesWithBrokenSymlinks(Path directory) throws IOException {
        List<Path> logs;
        try (var stream = Files.list(directory)) {
            logs = stream.filter(Files::isRegularFile).toList();
        }
        for (Path log : logs) {
            Files.delete(log);
            Files.createSymbolicLink(log, Path.of("allthelogs-missing-" + log.getFileName()));
        }
    }

    private static void writeCorruptGzipZip(Path archive, String entryPath) throws IOException {
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entryPath));
            zip.write(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
            zip.closeEntry();
        }
    }

    @BeforeEach
    void setUp() {
        store = LogStore.openInMemory();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private Path logsDirectory() throws IOException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writeGzipped(logs, "2026-08-24-1.log.gz",
            LogFixtures.modernLog("26.2", "alpha", "beta", "gamma"));
        LogFixtures.writeGzipped(logs, "2026-08-25-1.log.gz",
            LogFixtures.modernLog("26.2", "delta", "needle in here", "epsilon"));
        LogFixtures.writePlain(logs, "debug.log", LogFixtures.legacyLog("zeta", "another needle"));
        return tempDir.resolve("instance");
    }

    @Test
    void doesNotImportLatestLog() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "latest.log", LogFixtures.legacyLog("live"));
        LogFixtures.writePlain(tempDir.resolve("logs"), "Latest.LOG", LogFixtures.legacyLog("also live"));
        LogFixtures.writePlain(tempDir.resolve("logs"), "debug.log", LogFixtures.legacyLog("kept"));
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-26-1.log.gz",
            LogFixtures.modernLog("26.2", "gzipped"));

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(2, result.importedFiles());
        List<String> messages = store.allEntries().stream().map(ChatEntry::message).toList();
        assertTrue(messages.contains("kept"));
        assertTrue(messages.contains("gzipped"));
        assertFalse(messages.contains("live"));
        assertFalse(messages.contains("also live"));
        assertTrue(store.chatLogs().stream()
            .noneMatch(log -> fileName(log).equalsIgnoreCase("latest.log")));
    }

    @Test
    void doesNotImportLatestLogInsideAnArchive() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/latest.log", LogFixtures.legacyLog("live in zip"),
            "logs/debug.log", LogFixtures.legacyLog("kept in zip"))));

        ImportResult result = store.importArchive(archive);

        assertEquals(1, result.importedFiles());
        assertEquals(List.of("kept in zip"), store.allEntries().stream().map(ChatEntry::message).toList());
    }

    @Test
    void currentLogsImportTakesEveryLogExceptLatest() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writePlain(logs, "latest.log", LogFixtures.legacyLog("live"));
        LogFixtures.writePlain(logs, "debug.log", LogFixtures.legacyLog("debug"));
        LogFixtures.writeGzipped(logs, "2026-08-26-1.log.gz", LogFixtures.modernLog("26.2", "rotated"));

        ImportResult result = store.importDirectory(logs, ImportOptions.currentLogsDirectory());

        assertEquals(2, result.importedFiles());
        List<String> messages = store.allEntries().stream().map(ChatEntry::message).toList();
        assertTrue(messages.contains("debug"));
        assertTrue(messages.contains("rotated"));
        assertFalse(messages.contains("live"));
    }

    @Test
    void currentLogsImportIncludesNestedLogFiles() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-26-1.log.gz", LogFixtures.modernLog("26.2", "top"));
        LogFixtures.writeGzipped(logs.resolve("old"), "2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "nested"));
        LogFixtures.writePlain(logs, "latest.log", LogFixtures.legacyLog("live"));

        ImportResult result = store.importDirectory(logs, ImportOptions.currentLogsDirectory());

        assertEquals(2, result.importedFiles());
        List<String> messages = store.allEntries().stream().map(ChatEntry::message).toList();
        assertTrue(messages.contains("top"));
        assertTrue(messages.contains("nested"));
        assertFalse(messages.contains("live"));
    }

    @Test
    void currentGameDirectoryImportFindsLogsAndIgnoresResourcePackZips() throws IOException {
        Path gameDir = tempDir.resolve("instance");
        LogFixtures.writeGzipped(gameDir.resolve("logs"), "2026-08-26-1.log.gz",
            LogFixtures.modernLog("26.2", "from logs"));
        LogFixtures.writeGzipped(gameDir.resolve("logs/old"), "2026-01-02-1.log.gz",
            LogFixtures.modernLog("1.21.8", "from nested"));
        LogFixtures.writePlain(gameDir.resolve("logs"), "latest.log", LogFixtures.legacyLog("live"));
        LogFixtures.writeZip(gameDir.resolve("resourcepacks/pack.zip"), new LinkedHashMap<>(Map.of(
            "assets/pack.png", "not a log",
            "logs/2026-03-01-1.log.gz", LogFixtures.modernLog("1.21.8", "from resource pack"))));

        ImportResult result = store.importDirectory(gameDir, ImportOptions.currentGameDirectory());

        assertTrue(result.failures().isEmpty(), () -> "unexpected failures: " + result.failures());
        assertEquals(2, result.importedFiles());
        List<String> messages = store.allEntries().stream().map(ChatEntry::message).toList();
        assertTrue(messages.contains("from logs"));
        assertTrue(messages.contains("from nested"));
        assertFalse(messages.contains("live"));
        assertFalse(messages.contains("from resource pack"));
    }

    @Test
    void currentLogsImportSkipsOptimizeAndAcceptsASecondPass() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-26-1.log.gz", LogFixtures.modernLog("26.2", "first"));

        ImportResult first = store.importDirectory(logs, ImportOptions.currentLogsDirectory());
        assertEquals(1, first.importedFiles());

        LogFixtures.writeGzipped(logs, "2026-08-27-1.log.gz", LogFixtures.modernLog("26.2", "second"));
        ImportResult second = store.importDirectory(logs, ImportOptions.currentLogsDirectory());

        assertEquals(1, second.importedFiles());
        List<String> messages = store.allEntries().stream().map(ChatEntry::message).toList();
        assertTrue(messages.contains("first"));
        assertTrue(messages.contains("second"));
    }

    @Test
    void importsChatEntriesFromADirectory() throws IOException {
        ImportResult result = store.importDirectory(logsDirectory());

        assertTrue(result.failures().isEmpty(), () -> "unexpected failures: " + result.failures());
        assertEquals(3, result.importedFiles());
        assertEquals(8, result.importedEntries());
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void resolvesTheChatLogOfEveryEntry() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();
        LogSource.File source = assertInstanceOf(LogSource.File.class, entry.chatLog().source());
        Path expected = root.resolve("logs/2026-08-25-1.log.gz").toAbsolutePath().normalize();
        assertEquals(expected, source.path());
        assertEquals("2026-08-25-1.log.gz", source.path().getFileName().toString());
        assertEquals("26.2", entry.chatLog().minecraftVersion());
        assertEquals(LocalDate.of(2026, 8, 25), entry.chatLog().date());
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 0, 11), entry.timestamp());
    }

    @Test
    void storesTheMinecraftUserFromTheLog() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "debug.log", """
            [11:21:53] [Render thread/INFO]: Setting user: JustAlittleWolf
            [11:21:54] [Render thread/INFO]: [CHAT] hi from wolf
            """);
        store.importDirectory(tempDir);
        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("hi from wolf")).getFirst();
        assertEquals("JustAlittleWolf", entry.chatLog().minecraftUser());
    }

    @Test
    void reusesTheSameChatLogInstanceForEntriesFromTheSameFile() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all().withSubstring("needle in here").withContextLines(1));

        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
        assertSame(hits.get(0).chatLog(), hits.get(1).chatLog());
        assertSame(hits.get(1).chatLog(), hits.get(2).chatLog());

        List<ChatEntry> twoFiles = store.findEntries(ChatQuery.all().withSubstring("needle"));
        assertEquals(2, twoFiles.size());
        assertNotSame(twoFiles.get(0).chatLog(), twoFiles.get(1).chatLog());
    }

    @Test
    void datesFilesWithoutADateInTheirNameByLastModified() throws IOException {
        Path root = logsDirectory();
        Path undatedFile = root.resolve("logs/debug.log");
        Instant modified = Instant.parse("2026-08-20T12:00:00Z");
        Files.setLastModifiedTime(undatedFile, FileTime.from(modified));

        store.importDirectory(root);

        ChatLog undated = store.chatLogs().stream()
            .filter(file -> fileName(file).equals("debug.log")).findFirst().orElseThrow();
        assertEquals(modified.atZone(ZoneId.systemDefault()).toLocalDate(), undated.date());
        assertEquals("1.8.9", undated.minecraftVersion());
    }

    @Test
    void recordsFileMetadataIncludingEntryBounds() throws IOException {
        store.importDirectory(logsDirectory());

        ChatLog file = store.chatLogs().stream()
            .filter(f -> fileName(f).equals("2026-08-24-1.log.gz")).findFirst().orElseThrow();
        // Bounds cover every logged line of the file, not just its chat entries, so they start at the very first
        // line ("Loading Minecraft...") rather than the first [CHAT] line.
        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 0, 0), file.startTime());
        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 0, 13), file.endTime());
    }

    @Test
    void nonRecursiveImportIgnoresSubdirectories() throws IOException {
        ImportResult result = store.importDirectory(logsDirectory(),
            ImportOptions.defaults().withRecursive(false));

        assertEquals(0, result.importedFiles());
        assertTrue(store.allEntries().isEmpty());
    }

    @Test
    void pathMatcherRestrictsWhichFilesAreImported() throws IOException {
        Path root = logsDirectory();
        LogFixtures.writePlain(root.resolve("crash-reports"), "crash.log",
            LogFixtures.modernLog("26.2", "should not be imported"));

        store.importDirectory(root, ImportOptions.defaults().withPathMatcher("**/logs/**"));

        assertTrue(store.findEntries(ChatQuery.all().withSubstring("should not be imported")).isEmpty());
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void importsLogsFromAnArchive() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "in archive"))));

        ImportResult result = store.importArchive(archive);

        assertEquals(1, result.importedFiles());
        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("in archive")).getFirst();
        LogSource.Archive source = assertInstanceOf(LogSource.Archive.class, entry.chatLog().source());
        assertEquals(archive.toAbsolutePath().normalize(), source.path());
        assertEquals("logs/2026-01-02-1.log.gz", source.entryPath());
        assertEquals("1.21.8", entry.chatLog().minecraftVersion());
    }

    @Test
    void importingAnArchiveHonoursTheTimezone() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "in archive"))));
        ZoneOffset offset = ZoneOffset.ofHours(4);

        store.importArchive(archive, ImportOptions.defaults().withTimezone(offset));

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("in archive")).getFirst();
        assertEquals(LogDates.toSystemLocal(LocalDateTime.of(2026, 1, 2, 10, 0, 10), offset), entry.timestamp());
    }

    @Test
    void importsLogsFromArchivesNestedInsideArchives() throws IOException {
        byte[] inner = LogFixtures.zipBytes(Map.of("logs/2026-02-03-1.log", LogFixtures.legacyLog("deeply nested")));
        Path outer = tempDir.resolve("outer.zip");
        Files.createDirectories(outer.getParent());
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(outer))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("instances/inner.zip"));
            zip.write(inner);
            zip.closeEntry();
        }

        ImportResult result = store.importArchive(outer);

        assertEquals(1, result.importedFiles());
        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("deeply nested")).getFirst();
        LogSource.Archive source = assertInstanceOf(LogSource.Archive.class, entry.chatLog().source());
        assertEquals(outer.toAbsolutePath().normalize(), source.path());
        assertEquals("instances/inner.zip!/logs/2026-02-03-1.log", source.entryPath());
    }

    @Test
    void nestedArchivesCanBeDisabled() throws IOException {
        byte[] inner = LogFixtures.zipBytes(Map.of("logs/2026-02-03-1.log", LogFixtures.legacyLog("deeply nested")));
        Path outer = tempDir.resolve("outer.zip");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(outer))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("instances/inner.zip"));
            zip.write(inner);
            zip.closeEntry();
        }

        ImportResult result = store.importArchive(outer, ImportOptions.defaults().withNestedArchives(false));

        assertEquals(0, result.importedFiles());
    }

    @Test
    void directoryImportRecordsArchivesAgainstTheArchiveFile() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("instance/backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "from nested zip"))));

        store.importDirectory(tempDir);

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("from nested zip")).getFirst();
        LogSource.Archive source = assertInstanceOf(LogSource.Archive.class, entry.chatLog().source());
        assertEquals(archive.toAbsolutePath().normalize(), source.path());
        assertEquals("logs/2026-01-02-1.log.gz", source.entryPath());
    }

    @Test
    void reimportingReplacesInsteadOfDuplicating() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);
        store.importDirectory(root);

        assertEquals(3, store.chatLogs().size());
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void alreadyImportedFilesCanBeSkipped() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);

        ImportResult second = store.importDirectory(root, ImportOptions.defaults().withSkipAlreadyImported(true));

        assertEquals(0, second.importedFiles());
        assertEquals(3, second.skippedFiles());
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void alreadyImportedIsKeyedByTheLogFileNotTheImportRoot() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);

        ImportResult second = store.importDirectory(root.resolve("logs"),
            ImportOptions.defaults().withSkipAlreadyImported(true));

        assertEquals(0, second.importedFiles());
        assertEquals(3, second.skippedFiles());
        assertEquals(3, store.chatLogs().size());
    }

    @Test
    void alreadyImportedFilesAreSkippedWithoutOpeningThem() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);
        replaceLogFilesWithBrokenSymlinks(root.resolve("logs"));

        ImportResult second = store.importDirectory(root.resolve("logs"), ImportOptions.currentLogsDirectory());

        assertEquals(0, second.importedFiles(), () -> "failures=" + second.failures());
        assertEquals(3, second.skippedFiles());
        assertTrue(second.failures().isEmpty(), () -> "unexpected failures: " + second.failures());
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void consideredEmptyFilesAreSkippedWithoutOpeningThemAgain() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-26-1.log.gz", LogFixtures.modernLog("26.2"));

        ImportResult first = store.importDirectory(logs, ImportOptions.currentLogsDirectory());
        assertEquals(0, first.importedFiles());
        assertEquals(1, first.skippedFiles());

        replaceLogFilesWithBrokenSymlinks(logs);
        ImportResult second = store.importDirectory(logs, ImportOptions.currentLogsDirectory());

        assertEquals(0, second.importedFiles(), () -> "failures=" + second.failures());
        assertEquals(1, second.skippedFiles());
        assertTrue(second.failures().isEmpty(), () -> "unexpected failures: " + second.failures());
        assertTrue(store.allEntries().isEmpty());
    }

    @Test
    void consideredSessionDuplicatesAreSkippedWithoutOpeningThemAgain() throws IOException {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        ChatLog session = store.startSession("26.2", startedAt);
        String sessionId = ((LogSource.Session) session.source()).id();
        assertTrue(store.importSessionMessage("live capture", startedAt.plusSeconds(10)));

        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-26-1.log.gz",
            taggedLog(sessionId, "10:00:10", "live capture", "from the file"));
        ImportResult first = store.importDirectory(tempDir);
        assertEquals(0, first.importedFiles());
        assertEquals(1, first.skippedFiles());

        replaceLogFilesWithBrokenSymlinks(logs);
        ImportResult second = store.importDirectory(logs, ImportOptions.currentLogsDirectory());

        assertEquals(0, second.importedFiles(), () -> "failures=" + second.failures());
        assertEquals(1, second.skippedFiles());
        assertTrue(second.failures().isEmpty(), () -> "unexpected failures: " + second.failures());
        assertEquals(List.of("live capture"), store.allEntries().stream().map(ChatEntry::message).toList());
    }

    @Test
    void alreadyImportedArchiveEntriesAreSkippedWithoutReadingThem() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("26.2", "in archive"))));
        store.importArchive(archive);
        writeCorruptGzipZip(archive, "logs/2026-01-02-1.log.gz");

        ImportResult second = store.importArchive(archive, ImportOptions.defaults().withSkipAlreadyImported(true));

        assertEquals(0, second.importedFiles(), () -> "failures=" + second.failures());
        assertEquals(1, second.skippedFiles());
        assertTrue(second.failures().isEmpty(), () -> "unexpected failures: " + second.failures());
        assertEquals(List.of("in archive"), store.allEntries().stream().map(ChatEntry::message).toList());
    }

    @Test
    void searchesBySubstringCaseInsensitivelyByDefault() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(2, store.findEntries(ChatQuery.all().withSubstring("NEEDLE")).size());
        assertEquals(0, store.findEntries(ChatQuery.all().withSubstringCaseSensitive("NEEDLE")).size());
    }

    @Test
    void searchesByRegex() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all().withRegex("^(alpha|gamma)$"));
        assertEquals(List.of("alpha", "gamma"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void filtersByDateRangeExcludingTheUpperBound() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all()
            .startingAt(LocalDateTime.of(2026, 8, 25, 0, 0))
            .upUntil(LocalDateTime.of(2026, 8, 26, 0, 0)));
        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void combinesTextAndDateFilters() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all()
            .withSubstring("needle")
            .startingAt(LocalDateTime.of(2026, 8, 25, 0, 0))
            .upUntil(LocalDateTime.of(2026, 8, 26, 0, 0)));
        assertEquals(List.of("needle in here"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void filtersByMinecraftVersion() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(List.of("alpha", "beta", "gamma", "delta", "needle in here", "epsilon"),
            store.findEntries(ChatQuery.all().withVersion("26.2")).stream().map(ChatEntry::message).toList());
        assertEquals(List.of("zeta", "another needle"),
            store.findEntries(ChatQuery.all().withVersion("1.8.9")).stream().map(ChatEntry::message).toList());
        assertTrue(store.findEntries(ChatQuery.all().withVersion("1.20.1")).isEmpty());
    }

    @Test
    void versionFilterCombinesWithTextAndContext() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(List.of("needle in here"),
            store.findEntries(ChatQuery.all().withSubstring("needle").withVersion("26.2"))
                .stream().map(ChatEntry::message).toList());
        assertEquals(List.of("zeta", "another needle"),
            store.findEntries(ChatQuery.all().withSubstring("needle").withVersion("1.8.9").withContextLines(1))
                .stream().map(ChatEntry::message).toList());
    }

    @Test
    void returnsSurroundingLinesForMatches() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all().withSubstring("needle in here").withContextLines(1));
        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void aroundReturnsNeighboursFromTheSameLog() throws IOException {
        store.importDirectory(logsDirectory());
        ChatEntry hit = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();
        assertEquals(List.of("delta", "needle in here", "epsilon"),
            store.entriesAround(hit.chatLog(), hit.lineIndex(), 1).stream().map(ChatEntry::message).toList());
        assertEquals(List.of("delta", "needle in here"),
            store.entriesAround(hit.chatLog(), hit.lineIndex(), 1, 0).stream().map(ChatEntry::message).toList());
        assertEquals(List.of("needle in here", "epsilon"),
            store.entriesAround(hit.chatLog(), hit.lineIndex(), 0, 1).stream().map(ChatEntry::message).toList());
    }

    @Test
    void summarizeCoversMatchingDatesAndSumsDayCounts() throws IOException {
        store.importDirectory(logsDirectory());
        MatchSummary needles = store.summarizeMatches(ChatQuery.all().withSubstring("needle in here"));
        assertEquals(1, needles.uniqueDates());
        assertEquals(needles.oldest(), needles.newest());
        assertEquals(1, needles.matches());
        MatchSummary all = store.summarizeMatches(ChatQuery.all());
        assertTrue(all.uniqueDates() >= 2);
        assertFalse(all.dates().isEmpty());
        assertEquals(all.uniqueDates(), all.dates().size());
        assertEquals(all.dates().size(), all.days().size());
        assertTrue(all.days().getFirst().matches() >= 1);
        long summed = 0;
        for (MatchDay day : all.days()) {
            summed += day.matches();
        }
        assertEquals(summed, all.matches());
        assertEquals(store.countMatches(ChatQuery.all()), all.matches());
        assertEquals(store.findEntries(ChatQuery.all()).size(), all.matches());
    }

    @Test
    void matchesHonoursLimitAndIgnoresContext() throws IOException {
        store.importDirectory(logsDirectory());
        assertEquals(store.findEntries(ChatQuery.all()).size(), store.countMatches(ChatQuery.all()));
        assertEquals(1, store.countMatches(ChatQuery.all().withSubstring("needle in here")));
        ChatQuery withContext = ChatQuery.all().withSubstring("needle in here").withContextLines(1);
        assertEquals(1, store.countMatches(withContext));
        assertEquals(3, store.findEntries(withContext).size());
        assertEquals(2, store.countMatches(ChatQuery.all().withLimit(2)));
    }

    @Test
    void matchesHonoursTimestampOffset() throws IOException {
        importOffsetLog();
        assertEquals(3, store.countMatches(ChatQuery.all()
            .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 11))));
        assertEquals(2, store.countMatches(ChatQuery.all()
            .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 11))
            .withLimit(2)));
    }

    @Test
    void summarizeSkipEmptyMonthsBetweenHits() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2025-01-15-1.log.gz",
            LogFixtures.modernLog("26.2", "old hit"));
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-01-1.log.gz",
            LogFixtures.modernLog("26.2", "new hit"));
        store.importDirectory(tempDir);
        MatchSummary summary = store.summarizeMatches(ChatQuery.all().withSubstring("hit"));
        assertEquals(2, summary.dates().size());
        assertEquals(java.time.LocalDate.of(2025, 1, 15), summary.dates().getFirst());
        assertEquals(java.time.LocalDate.of(2026, 8, 1), summary.dates().getLast());
        assertEquals(2, summary.matches());
    }

    @Test
    void summarizeKeepOnlyTheDaysThatHaveHitsInAMonth() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-01-01-1.log.gz",
            LogFixtures.modernLog("26.2", "first of month"));
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-01-31-1.log.gz",
            LogFixtures.modernLog("26.2", "last of month"));
        store.importDirectory(tempDir);
        MatchSummary summary = store.summarizeMatches(ChatQuery.all().withSubstring("of month"));
        assertEquals(List.of(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 31)), summary.dates());
        assertEquals(2, summary.matches());
    }

    @Test
    void cancelledImportStopsBeforeFinishing() throws IOException {
        ImportResult result = store.importDirectory(logsDirectory(), ImportOptions.defaults(), null, () -> true);
        assertTrue(result.importedFiles() <= 3);
    }

    @Test
    void contextDoesNotLeakAcrossChatLogs() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all().withSubstring("delta").withContextLines(5));
        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void overlappingContextWindowsDoNotProduceDuplicates() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-03-01-1.log.gz",
            LogFixtures.modernLog("26.2", "a", "hit one", "b", "hit two", "c"));

        store.importDirectory(tempDir);
        List<ChatEntry> hits = store.findEntries(ChatQuery.all().withSubstring("hit").withContextLines(2));

        assertEquals(List.of("a", "hit one", "b", "hit two", "c"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void contextLinesStayInsideTheRequestedDateRange() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.findEntries(ChatQuery.all()
            .withSubstring("delta")
            .withContextLines(5)
            .startingAt(LocalDateTime.of(2026, 8, 25, 10, 0, 10))
            .upUntil(LocalDateTime.of(2026, 8, 25, 10, 0, 12)));
        assertEquals(List.of("delta", "needle in here"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void ordersAscendingByDefaultAndDescendingOnRequest() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> ascending = store.allEntries();
        List<ChatEntry> descending = store.findEntries(ChatQuery.all().withSort(Sort.DESCENDING));

        assertEquals(ascending.getFirst().message(), descending.getLast().message());
        assertEquals(ascending.getLast().message(), descending.getFirst().message());
        assertNotEquals(ascending.getFirst().message(), ascending.getLast().message());
    }

    @Test
    void honoursTheLimit() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(2, store.findEntries(ChatQuery.all().withLimit(2)).size());
    }

    @Test
    void sortsDescendingWithTheSortOption() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> bySort = store.findEntries(ChatQuery.all().withSort(Sort.DESCENDING));

        assertTrue(bySort.getFirst().timestamp().isAfter(bySort.getLast().timestamp()));
    }

    @Test
    void timestampOffsetPagesForwardAndBackward() throws IOException {
        importOffsetLog();

        List<String> forward = store.findEntries(ChatQuery.all()
                .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 11)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("hit", "four", "five"), forward);

        List<String> backward = store.findEntries(ChatQuery.all()
                .withSort(Sort.DESCENDING)
                .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 13)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("hit", "two", "one"), backward);
    }

    @Test
    void timestampOffsetComplementsTheLimit() throws IOException {
        importOffsetLog();

        List<ChatEntry> page1 = store.findEntries(ChatQuery.all().withLimit(2));
        assertEquals(List.of("one", "two"), page1.stream().map(ChatEntry::message).toList());

        List<ChatEntry> page2 = store.findEntries(ChatQuery.all()
            .withOffset(page1.getLast().timestamp())
            .withLimit(2));
        assertEquals(List.of("hit", "four"), page2.stream().map(ChatEntry::message).toList());

        List<ChatEntry> page3 = store.findEntries(ChatQuery.all()
            .withOffset(page2.getLast().timestamp())
            .withLimit(2));
        assertEquals(List.of("five"), page3.stream().map(ChatEntry::message).toList());
    }

    @Test
    void timestampOffsetPagesNewestFirst() throws IOException {
        importOffsetLog();

        List<ChatEntry> page1 = store.findEntries(ChatQuery.all()
            .withSort(Sort.DESCENDING)
            .withLimit(2));
        assertEquals(List.of("five", "four"), page1.stream().map(ChatEntry::message).toList());

        List<ChatEntry> page2 = store.findEntries(ChatQuery.all()
            .withSort(Sort.DESCENDING)
            .withOffset(page1.getLast().timestamp())
            .withLimit(2));
        assertEquals(List.of("hit", "two"), page2.stream().map(ChatEntry::message).toList());
    }

    @Test
    void contextLinesMayExtendBeyondTheTimestampOffset() throws IOException {
        importOffsetLog();

        List<String> after = store.findEntries(ChatQuery.all()
                .withSubstring("hit")
                .withContextLines(2)
                .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 11)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("one", "two", "hit", "four", "five"), after);

        List<String> before = store.findEntries(ChatQuery.all()
                .withSubstring("hit")
                .withContextLines(2)
                .withSort(Sort.DESCENDING)
                .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 13)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("five", "four", "hit", "two", "one"), before);
    }

    @Test
    void limitAppliesToMatchesBeforeContextIsExpanded() throws IOException {
        importOffsetLog();

        List<String> hits = store.findEntries(ChatQuery.all()
                .withSubstring("t")
                .withContextLines(1)
                .withLimit(1))
            .stream().map(ChatEntry::message).toList();
        // "two" is the first match for "t" in ascending order; context adds "one" and "hit".
        assertEquals(List.of("one", "two", "hit"), hits);
    }

    @Test
    void timeWindowStillClipsContextWhenAnOffsetIsSet() throws IOException {
        importOffsetLog();

        List<String> hits = store.findEntries(ChatQuery.all()
                .withSubstring("hit")
                .withContextLines(2)
                .withOffset(LocalDateTime.of(2026, 6, 1, 10, 0, 11))
                .startingAt(LocalDateTime.of(2026, 6, 1, 10, 0, 12))
                .upUntil(LocalDateTime.of(2026, 6, 1, 10, 0, 14)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("hit", "four"), hits);
    }

    @Test
    void startingAtAloneKeepsEntriesFromThatInstant() throws IOException {
        importOffsetLog();

        List<String> hits = store.findEntries(ChatQuery.all()
                .startingAt(LocalDateTime.of(2026, 6, 1, 10, 0, 12)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("hit", "four", "five"), hits);
    }

    @Test
    void upUntilAloneKeepsEntriesBeforeThatInstant() throws IOException {
        importOffsetLog();

        List<String> hits = store.findEntries(ChatQuery.all()
                .upUntil(LocalDateTime.of(2026, 6, 1, 10, 0, 12)))
            .stream().map(ChatEntry::message).toList();
        assertEquals(List.of("one", "two"), hits);
    }

    private void importOffsetLog() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-06-01-1.log.gz",
            LogFixtures.modernLog("26.2", "one", "two", "hit", "four", "five"));
        store.importDirectory(tempDir);
    }

    @Test
    void fetchesResultsThatSpanSeveralDuckDbChunks() throws IOException {
        StringBuilder log = new StringBuilder();
        log.append("[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");
        int lines = 2500;
        for (int i = 0; i < lines; i++) {
            int second = i % 60;
            int minute = (i / 60) % 60;
            int hour = 10 + (i / 3600);
            log.append(String.format(java.util.Locale.ROOT,
                "[%02d:%02d:%02d] [Render thread/INFO]: [CHAT] line %d%n", hour, minute, second, i));
        }
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-05-01-1.log", log.toString());

        store.importDirectory(tempDir);

        List<ChatEntry> entries = store.allEntries();
        assertEquals(lines, entries.size());
        assertEquals("line 0", entries.getFirst().message());
        assertEquals("line 2047", entries.get(2047).message());
        assertEquals("line 2048", entries.get(2048).message());
        assertEquals("line " + (lines - 1), entries.getLast().message());
        List<ChatEntry> aroundChunkBoundary = store.findEntries(ChatQuery.all()
            .withRegex("^line 2048$")
            .withContextLines(1));
        assertEquals(List.of("line 2047", "line 2048", "line 2049"),
            aroundChunkBoundary.stream().map(ChatEntry::message).toList());
    }

    @Test
    void persistsToASingleFileAcrossReopens() throws IOException {
        Path database = tempDir.resolve("logs.duckdb");
        Path root = logsDirectory();
        try (LogStore persistent = LogStore.open(database)) {
            persistent.importDirectory(root);
        }

        assertTrue(Files.isRegularFile(database));
        try (LogStore reopened = LogStore.open(database)) {
            assertEquals(8, reopened.allEntries().size());
            assertEquals(database, reopened.databasePath().orElseThrow());
            LogStoreMetadata metadata = reopened.metadata();
            assertEquals(3, metadata.chatLogCount());
            assertEquals(8, metadata.chatEntryCount());
            assertTrue(metadata.minecraftVersions().containsAll(List.of("26.2", "1.8.9")));
            assertEquals(onDiskSize(database), metadata.databaseSizeBytes());
            assertTrue(metadata.databaseSizeBytes() > 0);
        }
    }

    @Test
    void emptyLogStoreMetadataHasZeroCounts() {
        LogStoreMetadata metadata = store.metadata();

        assertTrue(metadata.minecraftVersions().isEmpty());
        assertNull(metadata.firstLogDate());
        assertNull(metadata.lastLogDate());
        assertEquals(0, metadata.chatLogCount());
        assertEquals(0, metadata.chatEntryCount());
        assertTrue(metadata.databaseSizeBytes() >= 0);
    }

    @Test
    void metadataSummarisesImportedLogs() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-20-1.log.gz", LogFixtures.legacyLog("old"));
        LogFixtures.writeGzipped(logs, "2026-08-24-1.log.gz", LogFixtures.modernLog("26.2", "one", "two"));
        store.importDirectory(tempDir);

        LogStoreMetadata metadata = store.metadata();
        assertEquals(List.of("1.8.9", "26.2"), metadata.minecraftVersions());
        assertEquals(LocalDate.of(2026, 8, 20), metadata.firstLogDate());
        assertEquals(LocalDate.of(2026, 8, 24), metadata.lastLogDate());
        assertEquals(2, metadata.chatLogCount());
        assertEquals(3, metadata.chatEntryCount());
        assertTrue(metadata.databaseSizeBytes() > 0);
    }

    @Test
    void reportsUnreadableArchivesAsFailuresInsteadOfThrowing() throws IOException {
        Path broken = tempDir.resolve("broken.zip");
        Files.write(broken, "definitely not a zip".getBytes());

        ImportResult result = store.importArchive(broken);

        assertEquals(0, result.importedFiles());
        assertEquals(1, result.failures().size());
    }

    @Test
    void rejectsMissingImportRoots() {
        Path missing = tempDir.resolve("nope");
        assertThrows(LogDataException.class, () -> store.importDirectory(missing));
        assertThrows(LogDataException.class, () -> store.importArchive(missing));
    }

    @Test
    void reportsMalformedRegexAsALogDataException() throws IOException {
        store.importDirectory(logsDirectory());
        LogDataException error = assertThrows(LogDataException.class,
            () -> store.findEntries(ChatQuery.all().withRegex("(unclosed")));
        assertTrue(error.getMessage().contains("could not run regex (unclosed"), error.getMessage());
        assertTrue(error.getMessage().contains("missing )"), error.getMessage());
        assertNotNull(error.getCause());
        assertTrue(error.getCause().getMessage() != null && !error.getCause().getMessage().isBlank(),
            () -> String.valueOf(error.getCause()));
    }

    @Test
    void reportsLookaheadRegexAsAnUnsupportedRe2Feature() throws IOException {
        store.importDirectory(logsDirectory());
        String pattern = "(?:^|[\\]\\)\\>»\\s])([a-zA-Z0-9_]{3,16})(?:\\s*»|:)\\s+(?!(?:Offline|Online)\\b)\\S+.*";
        LogDataException error = assertThrows(LogDataException.class,
            () -> store.findEntries(ChatQuery.all().withRegex(pattern)));
        assertTrue(error.getMessage().contains("negative lookahead"), error.getMessage());
        assertTrue(error.getMessage().contains("RE2"), error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    void skipsLogsWithoutTimestamps() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "broken.log",
            "this is not a minecraft log\nReloading ResourceManager: vanilla\n");

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(0, result.importedFiles());
        assertEquals(1, result.skippedFiles());
        assertEquals(0, result.emptyFiles());
        assertTrue(store.chatLogs().isEmpty());
    }

    @Test
    void skipsLogsWithoutAnyChatEntries() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-01-1.log",
            "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(0, result.importedFiles());
        assertEquals(1, result.skippedFiles());
        assertEquals(0, result.emptyFiles());
        assertFalse(store.chatLogs().stream().anyMatch(file -> fileName(file).equals("2026-04-01-1.log")));
    }

    @Test
    void logsWithResourceManagerReloadAreKeptEvenWithoutChatEntries() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-02-1.log",
            "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                + "[10:00:05] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric\n"
                + "[10:00:10] [Render thread/INFO]: done\n");

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(1, result.importedFiles());
        assertEquals(0, result.skippedFiles());
        assertEquals(1, result.emptyFiles());
        ChatLog file = store.chatLogs().stream()
            .filter(f -> fileName(f).equals("2026-04-02-1.log")).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 4, 2), file.date());
        assertEquals(LocalDateTime.of(2026, 4, 2, 10, 0, 0), file.startTime());
        assertEquals(LocalDateTime.of(2026, 4, 2, 10, 0, 10), file.endTime());
        assertTrue(store.allEntries().isEmpty());
    }

    @Test
    void fileEntryTimeBoundsCoverAllLoggedLinesNotJustChatEntries() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-03-1.log",
            "[09:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                + "[09:00:05] [Render thread/INFO]: [CHAT] hello\n"
                + "[09:00:10] [Render thread/INFO]: done\n");

        store.importDirectory(tempDir);

        ChatLog file = store.chatLogs().stream()
            .filter(f -> fileName(f).equals("2026-04-03-1.log")).findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 4, 3, 9, 0, 0), file.startTime());
        assertEquals(LocalDateTime.of(2026, 4, 3, 9, 0, 10), file.endTime());
    }

    @Test
    void startSessionCreatesAChatLogBeforeAnyChatIsImported() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);

        ChatLog file = store.startSession("26.2", startedAt);

        assertInstanceOf(LogSource.Session.class, file.source());
        assertTrue(SessionMarker.isId(((LogSource.Session) file.source()).id()));
        assertEquals("26.2", file.minecraftVersion());
        assertEquals(LocalDate.of(2026, 8, 26), file.date());
        assertEquals(startedAt, file.startTime());
        assertEquals(startedAt, file.endTime());
        assertNull(file.minecraftUser());
        assertEquals(1, store.chatLogs().size());
        assertTrue(store.allEntries().isEmpty());
    }

    @Test
    void startSessionStoresMinecraftUserOnTheChatLogAndQueries() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);

        ChatLog file = store.startSession("26.2", startedAt, "JustAlittleWolf");

        assertEquals("JustAlittleWolf", file.minecraftUser());
        assertEquals("JustAlittleWolf", store.chatLogs().getFirst().minecraftUser());

        assertTrue(store.importSessionMessage("hello from live", startedAt.plusSeconds(1)));
        ChatEntry queried = store.findEntries(ChatQuery.all().withSubstring("hello from live")).getFirst();
        assertEquals("JustAlittleWolf", queried.chatLog().minecraftUser());
    }

    @Test
    void importSessionMessageRequiresAnActiveSession() {
        assertThrows(LogDataException.class, () -> store.importSessionMessage("hello"));
    }

    @Test
    void updateSessionEndTimeRequiresAnActiveSession() {
        assertThrows(LogDataException.class, () -> store.updateSessionEndTime(LocalDateTime.now()));
    }

    @Test
    void updateSessionEndTimeDoesNotStoreAChatLine() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", startedAt);

        store.updateSessionEndTime(startedAt.plusHours(2));

        ChatLog file = store.chatLogs().getFirst();
        assertEquals(startedAt, file.startTime());
        assertEquals(startedAt.plusHours(2), file.endTime());
        assertTrue(store.allEntries().isEmpty());
    }

    @Test
    void updateSessionEndTimeDoesNotMoveEarlier() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", startedAt);
        store.importSessionMessage("later", startedAt.plusMinutes(5));

        store.updateSessionEndTime(startedAt.plusMinutes(1));

        ChatLog file = store.chatLogs().getFirst();
        assertEquals(startedAt.plusMinutes(5), file.endTime());
        assertEquals(1, store.allEntries().size());
    }

    @Test
    void updateSessionEndTimeCanAdvancePastTheLastMessage() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", startedAt);
        store.importSessionMessage("chat", startedAt.plusMinutes(5));

        store.updateSessionEndTime(startedAt.plusHours(1));

        ChatLog file = store.chatLogs().getFirst();
        assertEquals(startedAt, file.startTime());
        assertEquals(startedAt.plusHours(1), file.endTime());
        assertEquals(1, store.allEntries().size());
    }

    @Test
    void importingAnEarlierSessionMessageDoesNotMoveLastEntryTimeBack() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", startedAt);
        store.updateSessionEndTime(startedAt.plusHours(1));

        store.importSessionMessage("late note", startedAt.plusMinutes(1));

        ChatLog file = store.chatLogs().getFirst();
        assertEquals(startedAt.plusHours(1), file.endTime());
        assertEquals(1, store.allEntries().size());
    }

    @Test
    void storesClientEntriesAlongsideImportedOnes() throws IOException {
        store.importDirectory(logsDirectory());
        store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));

        assertTrue(store.importSessionMessage("client message", LocalDateTime.of(2026, 8, 26, 12, 0, 0)));

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("client message")).getFirst();
        assertInstanceOf(LogSource.Session.class, entry.chatLog().source());
        assertEquals("26.2", entry.chatLog().minecraftVersion());
        assertEquals(LocalDate.of(2026, 8, 26), entry.chatLog().date());
        assertEquals(LocalDateTime.of(2026, 8, 26, 12, 0, 0), entry.timestamp());
        assertEquals(9, store.allEntries().size());
    }

    @Test
    void clientImportsUpdateTheSessionLastTimestamp() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", startedAt);

        store.importSessionMessage("later", startedAt.plusMinutes(5));

        ChatLog file = store.chatLogs().getFirst();
        assertEquals(startedAt, file.startTime());
        assertEquals(startedAt.plusMinutes(5), file.endTime());
        assertEquals(1, store.allEntries().size());
    }

    @Test
    void clientEntriesUseTheCurrentTimeByDefault() {
        store.startSession("26.2");
        LocalDateTime before = LocalDateTime.now().withNano(0);
        assertTrue(store.importSessionMessage("now"));

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("now")).getFirst();
        assertFalse(entry.timestamp().isBefore(before));
        assertFalse(entry.timestamp().isAfter(LocalDateTime.now()));
    }

    @Test
    void clientEntriesStripFormattingCodes() {
        store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        store.importSessionMessage("\u00a7chello \u00a7aworld", LocalDateTime.of(2026, 8, 26, 12, 0, 0));

        ChatEntry entry = store.allEntries().getFirst();
        assertEquals("hello world", entry.message());
        long[] formatting = entry.formatting();
        assertEquals(PackedFormatting.color(0xFF5555), PackedFormatting.at(formatting, 0));
        assertEquals(PackedFormatting.color(0xFF5555), PackedFormatting.at(formatting, 5));
        assertEquals(PackedFormatting.color(0x55FF55), PackedFormatting.at(formatting, 6));
    }

    @Test
    void fileImportStoresFormattingAndLeavesPlainLinesNull() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "debug.log", """
            [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
            [10:00:10] [Render thread/INFO]: [CHAT] plain
            [10:00:11] [Render thread/INFO]: [CHAT] \u00a7cRed \u00a7lBold
            """);
        store.importDirectory(tempDir);
        ChatEntry plain = store.findEntries(ChatQuery.all().withSubstring("plain")).getFirst();
        ChatEntry styled = store.findEntries(ChatQuery.all().withSubstring("Red")).getFirst();
        assertNull(plain.formatting());
        assertEquals("Red Bold", styled.message());
        assertEquals(PackedFormatting.color(0xFF5555), PackedFormatting.at(styled.formatting(), 0));
        assertEquals(PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD,
            PackedFormatting.at(styled.formatting(), 4));
    }

    @Test
    void liveFlattenedFormattingIsStoredAsPackedRuns() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", at);
        int red = PackedFormatting.color(0xFF5555);
        long[] packed = {PackedFormatting.run(0, 3, red)};
        assertTrue(store.importSessionMessage("abc", packed, at));
        ChatEntry entry = store.allEntries().getFirst();
        assertEquals("abc", entry.message());
        assertEquals(red, PackedFormatting.at(entry.formatting(), 1));
    }

    @Test
    void consecutiveClientEntriesGetIncreasingLineIndices() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", base);
        store.importSessionMessage("first", base);
        store.importSessionMessage("second", base.plusSeconds(1));
        store.importSessionMessage("third", base.plusSeconds(2));

        assertEquals(List.of(0, 1, 2), store.allEntries().stream().map(ChatEntry::lineIndex).toList());
        ChatLog file = store.chatLogs().getFirst();
        assertEquals(base, file.startTime());
        assertEquals(base.plusSeconds(2), file.endTime());
    }

    @Test
    void queryKeepsLineIndexOrderWhenTimestampsMatch() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", at);
        store.importSessionMessage("first", at);
        store.importSessionMessage("second", at);
        store.importSessionMessage("third", at);

        List<ChatEntry> entries = store.findEntries(ChatQuery.all());
        assertEquals(List.of("first", "second", "third"), entries.stream().map(ChatEntry::message).toList());
        assertEquals(List.of(0, 1, 2), entries.stream().map(ChatEntry::lineIndex).toList());
        List<ChatEntry> newestFirst = store.findEntries(ChatQuery.all().withSort(Sort.DESCENDING));
        assertEquals(List.of("third", "second", "first"), newestFirst.stream().map(ChatEntry::message).toList());
    }

    @Test
    void skipPagesThroughMatchesThatShareATimestamp() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", at);
        store.importSessionMessage("one", at);
        store.importSessionMessage("two", at);
        store.importSessionMessage("three", at);
        store.importSessionMessage("four", at);
        store.importSessionMessage("five", at);

        List<ChatEntry> page1 = store.findEntries(ChatQuery.all().withLimit(2));
        assertEquals(List.of("one", "two"), page1.stream().map(ChatEntry::message).toList());

        List<ChatEntry> page2 = store.findEntries(ChatQuery.all()
            .withOffset(page1.getLast().timestamp().minusNanos(1))
            .withSkip(page1.size())
            .withLimit(2));
        assertEquals(List.of("three", "four"), page2.stream().map(ChatEntry::message).toList());

        List<ChatEntry> page3 = store.findEntries(ChatQuery.all()
            .withOffset(page2.getLast().timestamp().minusNanos(1))
            .withSkip(page1.size() + page2.size())
            .withLimit(2));
        assertEquals(List.of("five"), page3.stream().map(ChatEntry::message).toList());

        List<ChatEntry> dropped = store.findEntries(ChatQuery.all()
            .withOffset(page1.getLast().timestamp())
            .withLimit(2));
        assertTrue(dropped.isEmpty());
    }

    @Test
    void clientEntriesSupportContextLines() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", base);
        store.importSessionMessage("before", base);
        store.importSessionMessage("the needle", base.plusSeconds(1));
        store.importSessionMessage("after", base.plusSeconds(2));

        List<ChatEntry> hits = store.findEntries(ChatQuery.all().withSubstring("needle").withContextLines(1));
        assertEquals(List.of("before", "the needle", "after"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void eachSessionGetsItsOwnChatLog() {
        store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        store.importSessionMessage("a", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        store.startSession("26.2", LocalDateTime.of(2026, 8, 27, 12, 0, 0));
        store.importSessionMessage("b", LocalDateTime.of(2026, 8, 27, 12, 0, 0));
        store.startSession("1.21.8", LocalDateTime.of(2026, 8, 27, 13, 0, 0));
        store.importSessionMessage("c", LocalDateTime.of(2026, 8, 27, 13, 0, 0));

        assertEquals(3, store.chatLogs().size());
        assertEquals(3, store.allEntries().size());
        assertTrue(store.chatLogs().stream().allMatch(file -> file.source() instanceof LogSource.Session));
    }

    @Test
    void repeatedClientEntriesAreDroppedAsDuplicates() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", timestamp);
        assertTrue(store.importSessionMessage("duplicated", timestamp));
        assertFalse(store.importSessionMessage("duplicated", timestamp));

        assertEquals(1, store.allEntries().size());
    }

    @Test
    void clientEntryDuplicatingAnImportedOneIsDropped() throws IOException {
        store.importDirectory(logsDirectory());
        store.startSession("26.2", LocalDateTime.of(2026, 8, 25, 10, 0, 10));

        assertFalse(store.importSessionMessage("delta", LocalDateTime.of(2026, 8, 25, 10, 0, 10)));
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void emptySessionFilesSurviveALaterImportDedup() throws IOException {
        store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        store.importDirectory(logsDirectory());

        assertTrue(store.chatLogs().stream().anyMatch(file -> file.source() instanceof LogSource.Session));
        assertTrue(store.allEntries().stream().noneMatch(entry -> entry.chatLog().source() instanceof LogSource.Session));
    }

    @Test
    void identicalEntriesFromDifferentSourcesAreStoredOnce() throws IOException {
        String log = LogFixtures.modernLog("26.2", "shared line", "unique to first");
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-06-01-1.log.gz", log);
        LogFixtures.writeGzipped(tempDir.resolve("other"), "2026-06-01-1.log.gz",
            LogFixtures.modernLog("26.2", "shared line", "unique to second"));

        store.importDirectory(tempDir);

        assertEquals(1, store.findEntries(ChatQuery.all().withSubstring("shared line")).size());
        assertEquals(3, store.allEntries().size());
    }

    @Test
    void duplicatesAreAlsoRemovedAcrossSeparateImports() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("a/logs"), "2026-06-01-1.log.gz",
            LogFixtures.modernLog("26.2", "shared line"));
        LogFixtures.writeGzipped(tempDir.resolve("b/logs"), "2026-06-01-1.log.gz",
            LogFixtures.modernLog("26.2", "shared line"));

        store.importDirectory(tempDir.resolve("a"));
        store.importDirectory(tempDir.resolve("b"));

        assertEquals(1, store.allEntries().size());
        // The file left without entries is dropped rather than lingering with a count of zero.
        assertEquals(1, store.chatLogs().size());
    }

    @Test
    void deduplicationKeepsDistinctEntriesThatOnlyShareATimestamp() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", timestamp);
        store.importSessionMessage("one", timestamp);
        store.importSessionMessage("two", timestamp);

        assertEquals(2, store.allEntries().size());
    }

    @Test
    void eachSessionGetsAUniqueId() {
        ChatLog first = store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        ChatLog second = store.startSession("26.2", LocalDateTime.of(2026, 8, 27, 12, 0, 0));

        LogSource.Session firstId = assertInstanceOf(LogSource.Session.class, first.source());
        LogSource.Session secondId = assertInstanceOf(LogSource.Session.class, second.source());
        assertTrue(SessionMarker.isId(firstId.id()));
        assertTrue(SessionMarker.isId(secondId.id()));
        assertNotEquals(firstId.id(), secondId.id());
        assertEquals(firstId.id(), ((LogSource.Session) store.chatLogs().getFirst().source()).id());
    }

    @Test
    void logTaggedWithAStoredSessionIdIsSkipped() throws IOException {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        ChatLog session = store.startSession("26.2", startedAt);
        String sessionId = ((LogSource.Session) session.source()).id();
        assertTrue(store.importSessionMessage("live capture", startedAt.plusSeconds(10)));

        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-26-1.log.gz",
            taggedLog(sessionId, "10:00:10", "live capture", "from the file"));
        ImportResult result = store.importDirectory(tempDir);

        assertEquals(0, result.importedFiles());
        assertEquals(1, result.skippedFiles());
        assertEquals(List.of("live capture"), store.allEntries().stream().map(ChatEntry::message).toList());
        assertInstanceOf(LogSource.Session.class, store.allEntries().getFirst().chatLog().source());
    }

    @Test
    void logTaggedWithAnUnknownSessionIdIsImported() throws IOException {
        String otherId = SessionMarker.newId();
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-26-1.log.gz",
            taggedLog(otherId, "10:00:10", "from the file"));

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(1, result.importedFiles());
        assertEquals(0, result.skippedFiles());
        assertEquals(List.of("from the file"), store.allEntries().stream().map(ChatEntry::message).toList());
        assertInstanceOf(LogSource.File.class, store.allEntries().getFirst().chatLog().source());
    }

    @Test
    void logTaggedWithAForeignSessionIdIsImportedEvenIfALiveSessionExists() throws IOException {
        store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        String otherId = SessionMarker.newId();
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-26-1.log.gz",
            taggedLog(otherId, "10:00:10", "from an older session"));

        ImportResult result = store.importDirectory(tempDir, ImportOptions.currentLogsDirectory());

        assertEquals(1, result.importedFiles(), () -> "skipped=" + result.skippedFiles()
            + " failures=" + result.failures());
        assertEquals(0, result.skippedFiles());
        assertEquals(List.of("from an older session"),
            store.allEntries().stream()
                .filter(entry -> entry.chatLog().source() instanceof LogSource.File)
                .map(ChatEntry::message).toList());
    }

    @Test
    void severalSessionTaggedLogsImportWhenNoneOfThoseSessionsAreStored() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-24-1.log.gz",
            taggedLog(SessionMarker.newId(), "10:00:10", "day one"));
        LogFixtures.writeGzipped(logs, "2026-08-25-1.log.gz",
            taggedLog(SessionMarker.newId(), "10:00:10", "day two"));
        LogFixtures.writeGzipped(logs, "2026-08-26-1.log.gz",
            taggedLog(SessionMarker.newId(), "10:00:10", "day three"));

        ImportResult result = store.importDirectory(logs, ImportOptions.currentLogsDirectory());

        assertEquals(3, result.importedFiles(), () -> "skipped=" + result.skippedFiles()
            + " failures=" + result.failures());
        assertEquals(0, result.skippedFiles());
        assertEquals(3, store.allEntries().size());
    }

    @Test
    void untaggedLogsAreStillImportedWhenASessionExists() throws IOException {
        store.startSession("26.2", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        store.importDirectory(logsDirectory());

        assertEquals(8, store.allEntries().size());
        assertTrue(store.chatLogs().stream().anyMatch(log -> log.source() instanceof LogSource.Session));
        assertTrue(store.chatLogs().stream().anyMatch(log -> log.source() instanceof LogSource.File));
    }

    @Test
    void repeatedSessionMessagesAtDifferentTimesAreKept() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.startSession("26.2", first);
        assertTrue(store.importSessionMessage("gg", first));
        assertTrue(store.importSessionMessage("gg", first.plusSeconds(30)));

        assertEquals(2, store.allEntries().size());
    }

    @Test
    void fileLinesWithTheSameTextAtDifferentTimesAreNotCollapsed() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("a/logs"), "2026-08-26-1.log.gz",
            chatLog("26.2", new String[]{"12:00:00"}, new String[]{"gg"}));
        LogFixtures.writeGzipped(tempDir.resolve("b/logs"), "2026-08-26-1.log.gz",
            chatLog("26.2", new String[]{"12:00:30"}, new String[]{"gg"}));

        store.importDirectory(tempDir);

        assertEquals(2, store.allEntries().size());
    }

    @Test
    void importedFileMetadataStaysConsistentAfterDeduplication() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("a/logs"), "2026-06-01-1.log.gz",
            LogFixtures.modernLog("26.2", "shared", "only in a"));
        LogFixtures.writeGzipped(tempDir.resolve("b/logs"), "2026-06-01-1.log.gz",
            LogFixtures.modernLog("26.2", "shared", "only in b"));

        store.importDirectory(tempDir);

        assertEquals(3, store.allEntries().size());
        assertEquals(2, store.chatLogs().size());
    }

    @Test
    void importsManyFilesInParallel() throws IOException {
        Path logs = tempDir.resolve("logs");
        for (int day = 1; day <= 28; day++) {
            LogFixtures.writeGzipped(logs, String.format("2026-05-%02d-1.log.gz", day),
                LogFixtures.modernLog("26.2", "day " + day + " message", "filler"));
        }

        ImportResult result = store.importDirectory(tempDir, ImportOptions.defaults().withParallelism(8));

        assertEquals(28, result.importedFiles());
        assertEquals(56, result.importedEntries());
        assertEquals(1, store.findEntries(ChatQuery.all().withSubstring("day 17 message")).size());
    }

    @Test
    void defaultImportLeavesTimestampsInLocalTime() throws IOException {
        store.importDirectory(logsDirectory());

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 0, 11), entry.timestamp());
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 0, 0),
            entry.chatLog().startTime());
    }

    @Test
    void importingWithATimezoneConvertsTimestampsToLocal() throws IOException {
        Path root = logsDirectory();
        ZoneOffset plusThree = ZoneOffset.ofHours(3);
        ZoneOffset minusThree = ZoneOffset.ofHours(-3);

        ImportResult first = store.importDirectory(root, ImportOptions.defaults().withTimezone(plusThree));
        ChatEntry plus = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();

        store.close();
        store = LogStore.openInMemory();
        ImportResult second = store.importDirectory(root, ImportOptions.defaults().withTimezone(minusThree));
        ChatEntry minus = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();

        assertTrue(first.failures().isEmpty(), () -> "unexpected failures: " + first.failures());
        assertTrue(second.failures().isEmpty(), () -> "unexpected failures: " + second.failures());
        // 10:00 in UTC+3 is six hours earlier than 10:00 in UTC-3, after both are converted to local time.
        assertEquals(Duration.ofHours(6), Duration.between(plus.timestamp(), minus.timestamp()));
        assertEquals(plus.timestamp(), LogDates.toSystemLocal(
            LocalDateTime.of(2026, 8, 25, 10, 0, 11), plusThree));
        assertEquals(minus.timestamp(), LogDates.toSystemLocal(
            LocalDateTime.of(2026, 8, 25, 10, 0, 11), minusThree));
        assertEquals(LogDates.toSystemLocal(LocalDateTime.of(2026, 8, 25, 10, 0, 0), minusThree),
            minus.chatLog().startTime());
    }

    @Test
    void importTimezoneDoesNotChangeTheNamedLogDate() throws IOException {
        store.importDirectory(logsDirectory(), ImportOptions.defaults().withTimezone(ZoneOffset.ofHours(-10)));

        ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();
        assertEquals(LocalDate.of(2026, 8, 25), entry.chatLog().date());
    }

    @Test
    void importTimezoneShiftsTheDateFallbackForFilesWithoutADateInTheName() throws IOException {
        Path logs = tempDir.resolve("logs");
        Path undated = LogFixtures.writePlain(logs, "debug.log", LogFixtures.legacyLog("from undated"));
        Instant modified = Instant.parse("2026-08-25T22:00:00Z");
        Files.setLastModifiedTime(undated, FileTime.from(modified));

        store.importDirectory(tempDir, ImportOptions.defaults().withTimezone(ZoneOffset.UTC));
        ChatLog utc = store.chatLogs().getFirst();
        assertEquals(LocalDate.of(2026, 8, 25), utc.date());

        store.close();
        store = LogStore.openInMemory();
        store.importDirectory(tempDir, ImportOptions.defaults().withTimezone(ZoneOffset.ofHours(14)));
        ChatLog plusFourteen = store.chatLogs().getFirst();
        assertEquals(LocalDate.of(2026, 8, 26), plusFourteen.date());
    }

    @Test
    void importThreadsStopWhenTheImportFinishes() throws Exception {
        store.importDirectory(logsDirectory());
        assertNoLiveThreads("allthelogs-parse");
        assertNoLiveThreads("allthelogs-discovery");
    }

    @Test
    void cancelledImportStopsParserThreads() throws Exception {
        store.importDirectory(logsDirectory(), ImportOptions.defaults(), null, () -> true);
        assertNoLiveThreads("allthelogs-parse");
        assertNoLiveThreads("allthelogs-discovery");
    }

    private static void assertNoLiveThreads(String prefix) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        List<String> live = liveThreads(prefix);
        while (!live.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
            live = liveThreads(prefix);
        }
        List<String> remaining = live;
        assertEquals(List.of(), remaining, () -> "threads still running: " + remaining);
    }

    private static List<String> liveThreads(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .map(Thread::getName)
            .filter(name -> name.startsWith(prefix))
            .sorted()
            .toList();
    }
}
