package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Runs the importer against a real collection of Minecraft logs.
///
/// The dataset is not part of the repository, so these tests skip themselves unless `allthelogs.dataset` points at a
/// directory holding real instance folders and archives.
class RealDatasetTest {
    @TempDir
    Path tempDir;

    private static Path dataset() {
        String configured = System.getProperty("allthelogs.dataset");
        return configured == null ? null : Path.of(configured);
    }

    @Test
    void importsRealInstanceDirectories() {
        Path dataset = dataset();
        assumeTrue(dataset != null && Files.isDirectory(dataset), "dataset not available");

        try (LogStore store = LogStore.open(tempDir.resolve("dataset.duckdb"))) {
            ImportResult result = store.importDirectory(dataset,
                    ImportOptions.defaults().withPathMatcher("**/logs/**"));

            assertTrue(result.importedFiles() > 0, "expected to import at least one log file");
            assertTrue(result.importedEntries() > 0, "expected to import at least one chat entry");
            assertTrue(result.failures().isEmpty(), () -> "unexpected failures: " + result.failures());

            List<LogFile> files = store.logFiles();
            assertTrue(files.stream().allMatch(file -> file.entryCount() > 0));
            assertTrue(files.stream().anyMatch(file -> file.dateSource() == DateSource.FILE_NAME));

            // Every log in the dataset comes from a launcher that writes a recognisable version line.
            Map<String, Long> versions = files.stream().collect(
                    Collectors.groupingBy(LogFile::minecraftVersion, Collectors.counting()));
            assertTrue(versions.size() > 1, () -> "expected several Minecraft versions, got " + versions);

            assertEquals(result.importedEntries(), store.count(ChatQuery.all()));
        }
    }

    @Test
    void importsRealArchivesIncludingNestedOnes() throws Exception {
        Path dataset = dataset();
        assumeTrue(dataset != null && Files.isDirectory(dataset), "dataset not available");
        List<Path> archives;
        try (var stream = Files.list(dataset)) {
            archives = stream.filter(path -> {
                String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                return name.endsWith(".zip") || name.endsWith(".7z");
            }).limit(3).toList();
        }
        assumeTrue(!archives.isEmpty(), "no archives in dataset");

        try (LogStore store = LogStore.open(tempDir.resolve("archives.duckdb"))) {
            long entries = 0;
            for (Path archive : archives) {
                ImportResult result = store.importArchive(archive);
                entries += result.importedEntries();
            }
            assertTrue(entries > 0, "expected to import entries from archives");
            assertTrue(store.logFiles().stream().allMatch(file -> file.sourceKind() == SourceKind.ARCHIVE));
        }
    }

    @Test
    void queriesRealDataByTextAndDateRange() {
        Path dataset = dataset();
        assumeTrue(dataset != null && Files.isDirectory(dataset), "dataset not available");

        try (LogStore store = LogStore.open(tempDir.resolve("queries.duckdb"))) {
            store.importDirectory(dataset, ImportOptions.defaults().withPathMatcher("**/logs/**"));
            assumeTrue(store.count(ChatQuery.all()) > 0, "no entries imported");

            LogFile earliest = store.logFiles().getFirst();
            LogFile latest = store.logFiles().getLast();
            LocalDateTime from = earliest.date().atStartOfDay();
            LocalDateTime to = latest.date().plusDays(1).atStartOfDay();

            assertEquals(store.count(ChatQuery.all()), store.count(ChatQuery.all().withRange(from, to)));

            List<ChatEntry> withContext = store.query(ChatQuery.all()
                    .withRegex("(?i)joined the game|left the game|<")
                    .withContextLines(2)
                    .withLimit(500));
            assertFalse(withContext.isEmpty(), "expected chat-like lines in real logs");

            // Context lines must never duplicate an entry, no matter how densely the matches cluster.
            long distinct = withContext.stream()
                    .map(entry -> entry.logFile().entryPath() + "#" + entry.lineIndex())
                    .distinct().count();
            assertEquals(withContext.size(), distinct);
        }
    }
}
