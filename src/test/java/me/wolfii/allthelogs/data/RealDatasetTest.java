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

/**
 * Runs the importer against a real collection of Minecraft logs.
 * <p>
 * The dataset is not part of the repository, so these tests skip themselves unless {@code allthelogs.dataset} points
 * at a directory holding real instance folders and archives.
 */
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

            List<ChatLog> files = store.chatLogs();
            assertEquals(files.size(), store.logEntries().stream().map(ChatEntry::chatLog).distinct().count());

            // Every log in the dataset comes from a launcher that writes a recognisable version line.
            Map<String, Long> versions = files.stream().collect(
                    Collectors.groupingBy(ChatLog::minecraftVersion, Collectors.counting()));
            assertTrue(versions.size() > 1, () -> "expected several Minecraft versions, got " + versions);

            assertEquals(result.importedEntries(), store.logEntries().size());
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
            assertTrue(store.chatLogs().stream().allMatch(file -> file.source() instanceof LogSource.Archive));
        }
    }

    @Test
    void queriesRealDataByTextAndDateRange() {
        Path dataset = dataset();
        assumeTrue(dataset != null && Files.isDirectory(dataset), "dataset not available");

        try (LogStore store = LogStore.open(tempDir.resolve("queries.duckdb"))) {
            store.importDirectory(dataset, ImportOptions.defaults().withPathMatcher("**/logs/**"));
            assumeTrue(!store.logEntries().isEmpty(), "no entries imported");

            ChatLog earliest = store.chatLogs().getFirst();
            ChatLog latest = store.chatLogs().getLast();
            LocalDateTime from = earliest.date().atStartOfDay();
            LocalDateTime to = latest.date().plusDays(1).atStartOfDay();

            assertEquals(store.logEntries().size(), store.query(ChatQuery.all().startingAt(from).upUntil(to)).size());

            List<ChatEntry> withContext = store.query(ChatQuery.all()
                    .withRegex("(?i)joined the game|left the game|<")
                    .withContextLines(2)
                    .withLimit(500));
            assertFalse(withContext.isEmpty(), "expected chat-like lines in real logs");

            // Context lines must never duplicate an entry, no matter how densely the matches cluster.
            long distinct = withContext.stream()
                    .map(entry -> entryPath(entry.chatLog().source()) + "#" + entry.lineIndex())
                    .distinct().count();
            assertEquals(withContext.size(), distinct);
        }
    }

    private static String entryPath(LogSource source) {
        return switch (source) {
            case LogSource.File file -> file.path().toString();
            case LogSource.Archive archive -> archive.path() + "!" + archive.entryPath();
            case LogSource.Session session -> "session";
        };
    }
}
