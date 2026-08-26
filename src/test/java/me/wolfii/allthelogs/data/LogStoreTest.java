package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogStoreTest {
    @TempDir
    Path tempDir;

    private LogStore store;

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
        LogFixtures.writePlain(logs, "latest.log", LogFixtures.legacyLog("zeta", "another needle"));
        return tempDir.resolve("instance");
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
    void resolvesTheLogFileOfEveryEntry() throws IOException {
        store.importDirectory(logsDirectory());

        ChatEntry entry = store.search("needle in here").getFirst();
        assertEquals("2026-08-25-1.log.gz", entry.logFile().fileName());
        assertEquals("logs/2026-08-25-1.log.gz", entry.logFile().entryPath());
        assertEquals(SourceKind.DIRECTORY, entry.logFile().sourceKind());
        assertEquals("26.2", entry.logFile().minecraftVersion());
        assertEquals(LocalDate.of(2026, 8, 25), entry.logFile().date());
        assertEquals(DateSource.FILE_NAME, entry.logFile().dateSource());
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 0, 11), entry.timestamp());
    }

    @Test
    void datesFilesWithoutADateInTheirNameByLastModified() throws IOException {
        store.importDirectory(logsDirectory());

        LogFile latest = store.logFiles().stream()
                .filter(file -> file.fileName().equals("latest.log")).findFirst().orElseThrow();
        assertEquals(DateSource.LAST_MODIFIED, latest.dateSource());
        assertEquals(latest.lastModified().orElseThrow().toLocalDate(), latest.date());
        assertEquals("1.8.9", latest.minecraftVersion());
    }

    @Test
    void recordsFileMetadataIncludingEntryBounds() throws IOException {
        store.importDirectory(logsDirectory());

        LogFile file = store.logFiles().stream()
                .filter(f -> f.fileName().equals("2026-08-24-1.log.gz")).findFirst().orElseThrow();
        assertEquals(3, file.entryCount());
        // Bounds cover every logged line of the file, not just its chat entries, so they start at the very first
        // line ("Loading Minecraft...") rather than the first [CHAT] line.
        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 0, 0), file.firstEntryTime().orElseThrow());
        assertEquals(LocalDateTime.of(2026, 8, 24, 10, 0, 13), file.lastEntryTime().orElseThrow());
        assertTrue(file.lastModified().isPresent());
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

        assertTrue(store.search("should not be imported").isEmpty());
        assertEquals(8, store.allEntries().size());
    }

    @Test
    void importsLogsFromAnArchive() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
                "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "in archive"))));

        ImportResult result = store.importArchive(archive);

        assertEquals(1, result.importedFiles());
        ChatEntry entry = store.search("in archive").getFirst();
        assertEquals(SourceKind.ARCHIVE, entry.logFile().sourceKind());
        assertEquals("logs/2026-01-02-1.log.gz", entry.logFile().entryPath());
        assertEquals("1.21.8", entry.logFile().minecraftVersion());
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
        ChatEntry entry = store.search("deeply nested").getFirst();
        assertEquals("instances/inner.zip!/logs/2026-02-03-1.log", entry.logFile().entryPath());
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
    void reimportingReplacesInsteadOfDuplicating() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);
        store.importDirectory(root);

        assertEquals(3, store.logFiles().size());
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
    void searchesBySubstringCaseInsensitivelyByDefault() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(2, store.search("NEEDLE").size());
        assertEquals(0, store.query(ChatQuery.all().withSubstringCaseSensitive("NEEDLE")).size());
    }

    @Test
    void searchesByRegex() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.query(ChatQuery.all().withRegex("^(alpha|gamma)$"));
        assertEquals(List.of("alpha", "gamma"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void filtersByDateRangeExcludingTheUpperBound() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.inRange(
                LocalDateTime.of(2026, 8, 25, 0, 0), LocalDateTime.of(2026, 8, 26, 0, 0));
        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void combinesTextAndDateFilters() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.query(ChatQuery.all()
                .withSubstring("needle")
                .withRange(LocalDateTime.of(2026, 8, 25, 0, 0), LocalDateTime.of(2026, 8, 26, 0, 0)));
        assertEquals(List.of("needle in here"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void returnsSurroundingLinesForMatches() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.query(ChatQuery.all().withSubstring("needle in here").withContextLines(1));
        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void contextDoesNotLeakAcrossLogFiles() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.query(ChatQuery.all().withSubstring("delta").withContextLines(5));
        assertEquals(List.of("delta", "needle in here", "epsilon"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void overlappingContextWindowsDoNotProduceDuplicates() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-03-01-1.log.gz",
                LogFixtures.modernLog("26.2", "a", "hit one", "b", "hit two", "c"));

        store.importDirectory(tempDir);
        List<ChatEntry> hits = store.query(ChatQuery.all().withSubstring("hit").withContextLines(2));

        assertEquals(List.of("a", "hit one", "b", "hit two", "c"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void contextLinesStayInsideTheRequestedDateRange() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> hits = store.query(ChatQuery.all()
                .withSubstring("delta")
                .withContextLines(5)
                .withRange(LocalDateTime.of(2026, 8, 25, 10, 0, 10), LocalDateTime.of(2026, 8, 25, 10, 0, 12)));
        assertEquals(List.of("delta", "needle in here"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void ordersAscendingByDefaultAndDescendingOnRequest() throws IOException {
        store.importDirectory(logsDirectory());

        List<ChatEntry> ascending = store.allEntries();
        List<ChatEntry> descending = store.query(ChatQuery.all().withDescending(true));

        assertEquals(ascending.getFirst().message(), descending.getLast().message());
        assertEquals(ascending.getLast().message(), descending.getFirst().message());
        assertNotEquals(ascending.getFirst().message(), ascending.getLast().message());
    }

    @Test
    void honoursTheLimit() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(2, store.query(ChatQuery.all().withLimit(2)).size());
    }

    @Test
    void countsMatchesWithoutMaterialisingThem() throws IOException {
        store.importDirectory(logsDirectory());

        assertEquals(8, store.count(ChatQuery.all()));
        assertEquals(2, store.count(ChatQuery.all().withSubstring("needle")));
    }

    @Test
    void streamsEntriesToAConsumer() throws IOException {
        store.importDirectory(logsDirectory());

        var messages = new java.util.ArrayList<String>();
        store.forEach(ChatQuery.all(), entry -> messages.add(entry.message()));

        assertEquals(8, messages.size());
    }

    @Test
    void persistsToASingleFileAcrossReopens() throws IOException {
        Path database = tempDir.resolve("logs.duckdb");
        Path root = logsDirectory();
        try (LogStore persistent = LogStore.open(database)) {
            persistent.importDirectory(root);
            persistent.compact();
        }

        assertTrue(Files.isRegularFile(database));
        try (LogStore reopened = LogStore.open(database)) {
            assertEquals(8, reopened.allEntries().size());
            assertEquals(database, reopened.databasePath().orElseThrow());
        }
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
        assertThrows(LogDataException.class, () -> store.query(ChatQuery.all().withRegex("(unclosed")));
    }

    @Test
    void skipsLogsWithoutAnyChatEntries() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-01-1.log",
                "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(0, result.importedFiles());
        assertEquals(1, result.emptyFiles());
        assertFalse(store.logFiles().stream().anyMatch(file -> file.fileName().equals("2026-04-01-1.log")));
    }

    @Test
    void logsWithResourceManagerReloadAreKeptEvenWithoutChatEntries() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-02-1.log",
                "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                    + "[10:00:05] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric\n"
                    + "[10:00:10] [Render thread/INFO]: done\n");

        ImportResult result = store.importDirectory(tempDir);

        assertEquals(1, result.importedFiles());
        assertEquals(0, result.emptyFiles());
        LogFile file = store.logFiles().stream()
                .filter(f -> f.fileName().equals("2026-04-02-1.log")).findFirst().orElseThrow();
        assertEquals(0, file.entryCount());
        assertEquals(LocalDate.of(2026, 4, 2), file.date());
        assertEquals(LocalDateTime.of(2026, 4, 2, 10, 0, 0), file.firstEntryTime().orElseThrow());
        assertEquals(LocalDateTime.of(2026, 4, 2, 10, 0, 10), file.lastEntryTime().orElseThrow());
    }

    @Test
    void fileEntryTimeBoundsCoverAllLoggedLinesNotJustChatEntries() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-03-1.log",
                "[09:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
                    + "[09:00:05] [Render thread/INFO]: [CHAT] hello\n"
                    + "[09:00:10] [Render thread/INFO]: done\n");

        store.importDirectory(tempDir);

        LogFile file = store.logFiles().stream()
                .filter(f -> f.fileName().equals("2026-04-03-1.log")).findFirst().orElseThrow();
        assertEquals(LocalDateTime.of(2026, 4, 3, 9, 0, 0), file.firstEntryTime().orElseThrow());
        assertEquals(LocalDateTime.of(2026, 4, 3, 9, 0, 10), file.lastEntryTime().orElseThrow());
    }

    @Test
    void storesLiveEntriesAlongsideImportedOnes() throws IOException {
        store.importDirectory(logsDirectory());

        assertTrue(store.addLiveEntry("26.2", "live message", LocalDateTime.of(2026, 8, 26, 12, 0, 0)));

        ChatEntry entry = store.search("live message").getFirst();
        assertEquals(SourceKind.LIVE, entry.logFile().sourceKind());
        assertEquals("26.2", entry.logFile().minecraftVersion());
        assertEquals(LocalDate.of(2026, 8, 26), entry.logFile().date());
        assertEquals(LocalDateTime.of(2026, 8, 26, 12, 0, 0), entry.timestamp());
        assertEquals(9, store.count(ChatQuery.all()));
    }

    @Test
    void liveEntriesUseTheCurrentTimeByDefault() {
        LocalDateTime before = LocalDateTime.now().withNano(0);
        assertTrue(store.addLiveEntry("26.2", "now"));

        ChatEntry entry = store.search("now").getFirst();
        assertFalse(entry.timestamp().isBefore(before));
        assertFalse(entry.timestamp().isAfter(LocalDateTime.now()));
    }

    @Test
    void liveEntriesStripFormattingCodes() {
        store.addLiveEntry("26.2", "\u00a7chello \u00a7aworld", LocalDateTime.of(2026, 8, 26, 12, 0, 0));

        assertEquals("hello world", store.allEntries().getFirst().message());
    }

    @Test
    void consecutiveLiveEntriesGetIncreasingLineIndices() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.addLiveEntry("26.2", "first", base);
        store.addLiveEntry("26.2", "second", base.plusSeconds(1));
        store.addLiveEntry("26.2", "third", base.plusSeconds(2));

        assertEquals(List.of(0, 1, 2), store.allEntries().stream().map(ChatEntry::lineIndex).toList());
        LogFile file = store.logFiles().getFirst();
        assertEquals(3, file.entryCount());
        assertEquals(base, file.firstEntryTime().orElseThrow());
        assertEquals(base.plusSeconds(2), file.lastEntryTime().orElseThrow());
    }

    @Test
    void liveEntriesSupportContextLines() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.addLiveEntry("26.2", "before", base);
        store.addLiveEntry("26.2", "the needle", base.plusSeconds(1));
        store.addLiveEntry("26.2", "after", base.plusSeconds(2));

        List<ChatEntry> hits = store.query(ChatQuery.all().withSubstring("needle").withContextLines(1));
        assertEquals(List.of("before", "the needle", "after"), hits.stream().map(ChatEntry::message).toList());
    }

    @Test
    void liveEntriesAreSeparatedByDayAndVersion() {
        store.addLiveEntry("26.2", "a", LocalDateTime.of(2026, 8, 26, 12, 0, 0));
        store.addLiveEntry("26.2", "b", LocalDateTime.of(2026, 8, 27, 12, 0, 0));
        store.addLiveEntry("1.21.8", "c", LocalDateTime.of(2026, 8, 27, 13, 0, 0));

        assertEquals(3, store.logFiles().size());
        assertTrue(store.logFiles().stream().allMatch(file -> file.entryCount() == 1));
    }

    @Test
    void repeatedLiveEntriesAreDroppedAsDuplicates() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        assertTrue(store.addLiveEntry("26.2", "duplicated", timestamp));
        assertFalse(store.addLiveEntry("26.2", "duplicated", timestamp));

        assertEquals(1, store.count(ChatQuery.all()));
    }

    @Test
    void liveEntryDuplicatingAnImportedOneIsDropped() throws IOException {
        store.importDirectory(logsDirectory());

        assertFalse(store.addLiveEntry("26.2", "delta", LocalDateTime.of(2026, 8, 25, 10, 0, 10)));
        assertEquals(8, store.count(ChatQuery.all()));
    }

    @Test
    void identicalEntriesFromDifferentSourcesAreStoredOnce() throws IOException {
        String log = LogFixtures.modernLog("26.2", "shared line", "unique to first");
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-06-01-1.log.gz", log);
        LogFixtures.writeGzipped(tempDir.resolve("other"), "2026-06-01-1.log.gz",
                LogFixtures.modernLog("26.2", "shared line", "unique to second"));

        store.importDirectory(tempDir);

        assertEquals(1, store.search("shared line").size());
        assertEquals(3, store.count(ChatQuery.all()));
    }

    @Test
    void duplicatesAreAlsoRemovedAcrossSeparateImports() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("a/logs"), "2026-06-01-1.log.gz",
                LogFixtures.modernLog("26.2", "shared line"));
        LogFixtures.writeGzipped(tempDir.resolve("b/logs"), "2026-06-01-1.log.gz",
                LogFixtures.modernLog("26.2", "shared line"));

        store.importDirectory(tempDir.resolve("a"));
        store.importDirectory(tempDir.resolve("b"));

        assertEquals(1, store.count(ChatQuery.all()));
        // The file left without entries is dropped rather than lingering with a count of zero.
        assertEquals(1, store.logFiles().size());
    }

    @Test
    void deduplicationKeepsDistinctEntriesThatOnlyShareATimestamp() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        store.addLiveEntry("26.2", "one", timestamp);
        store.addLiveEntry("26.2", "two", timestamp);

        assertEquals(0, store.deduplicate());
        assertEquals(2, store.count(ChatQuery.all()));
    }

    @Test
    void importedFileMetadataStaysConsistentAfterDeduplication() throws IOException {
        LogFixtures.writeGzipped(tempDir.resolve("a/logs"), "2026-06-01-1.log.gz",
                LogFixtures.modernLog("26.2", "shared", "only in a"));
        LogFixtures.writeGzipped(tempDir.resolve("b/logs"), "2026-06-01-1.log.gz",
                LogFixtures.modernLog("26.2", "shared", "only in b"));

        store.importDirectory(tempDir);

        long counted = store.count(ChatQuery.all());
        long summed = store.logFiles().stream().mapToLong(LogFile::entryCount).sum();
        assertEquals(counted, summed);
        assertEquals(3, counted);
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
        assertEquals(1, store.search("day 17 message").size());
    }
}
