package me.wolfii.allthelogs.data;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// End to end coverage of the advertised portable file: write a database, close it, open it again, and query.
class LogStorePersistenceTest {
    @TempDir
    Path tempDir;

    private Path database() {
        return tempDir.resolve("logs.duckdb");
    }

    private Path instanceLogs() throws IOException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writeGzipped(logs, "2026-08-24-1.log.gz",
                LogFixtures.modernLog("26.2", "alpha", "beta", "gamma"));
        LogFixtures.writeGzipped(logs, "2026-08-25-1.log.gz",
                LogFixtures.modernLog("26.2", "delta", "needle in here", "epsilon"));
        LogFixtures.writePlain(logs, "latest.log", LogFixtures.legacyLog("zeta", "another needle"));
        return tempDir.resolve("instance");
    }

    @Test
    void importedEntriesSurviveReopenAndRemainQueryable() throws IOException {
        Path database = database();
        Path root = instanceLogs();

        try (LogStore store = LogStore.open(database)) {
            ImportResult result = store.importDirectory(root);
            assertTrue(result.failures().isEmpty(), () -> "unexpected failures: " + result.failures());
            assertEquals(3, result.importedFiles());
            assertEquals(8, result.importedEntries());
        }

        assertTrue(Files.isRegularFile(database));
        try (LogStore store = LogStore.open(database)) {
            assertEquals(database, store.databasePath().orElseThrow());
            assertEquals(8, store.logEntries().size());
            assertEquals(3, store.chatLogs().size());

            assertEquals(2, store.query(ChatQuery.all().withSubstring("NEEDLE")).size());
            assertEquals(0, store.query(ChatQuery.all().withSubstringCaseSensitive("NEEDLE")).size());

            List<ChatEntry> regex = store.query(ChatQuery.all().withRegex("^(alpha|gamma)$"));
            assertEquals(List.of("alpha", "gamma"), regex.stream().map(ChatEntry::message).toList());

            List<ChatEntry> range = store.query(ChatQuery.all().withRange(
                    LocalDateTime.of(2026, 8, 25, 0, 0), LocalDateTime.of(2026, 8, 26, 0, 0)));
            assertEquals(List.of("delta", "needle in here", "epsilon"),
                    range.stream().map(ChatEntry::message).toList());

            List<ChatEntry> combined = store.query(ChatQuery.all()
                    .withSubstring("needle")
                    .withRange(LocalDateTime.of(2026, 8, 25, 0, 0), LocalDateTime.of(2026, 8, 26, 0, 0)));
            assertEquals(List.of("needle in here"), combined.stream().map(ChatEntry::message).toList());

            List<ChatEntry> withContext = store.query(ChatQuery.all()
                    .withSubstring("needle in here")
                    .withContextLines(1));
            assertEquals(List.of("delta", "needle in here", "epsilon"),
                    withContext.stream().map(ChatEntry::message).toList());

            ChatEntry needle = store.query(ChatQuery.all().withSubstring("needle in here")).getFirst();
            LogSource.File source = assertInstanceOf(LogSource.File.class, needle.chatLog().source());
            assertEquals(root.resolve("logs/2026-08-25-1.log.gz").toAbsolutePath().normalize(), source.path());
            assertEquals("26.2", needle.chatLog().minecraftVersion());
            assertEquals(LocalDate.of(2026, 8, 25), needle.chatLog().date());
            assertEquals(LocalDateTime.of(2026, 8, 25, 10, 0, 11), needle.timestamp());
        }
    }

    @Test
    void aLaterOpenCanAppendAndQueryTheCombinedHistory() throws IOException {
        Path database = database();
        Path first = tempDir.resolve("first");
        LogFixtures.writeGzipped(first.resolve("logs"), "2026-08-24-1.log.gz",
                LogFixtures.modernLog("26.2", "first session", "shared later"));

        try (LogStore store = LogStore.open(database)) {
            store.importDirectory(first);
        }

        Path second = tempDir.resolve("second");
        LogFixtures.writeGzipped(second.resolve("logs"), "2026-08-25-1.log.gz",
                LogFixtures.modernLog("26.2", "second session"));

        try (LogStore store = LogStore.open(database)) {
            ImportResult skipped = store.importDirectory(first, ImportOptions.defaults().withSkipAlreadyImported(true));
            assertEquals(0, skipped.importedFiles());
            assertEquals(1, skipped.skippedFiles());

            store.importDirectory(second);
        }

        try (LogStore store = LogStore.open(database)) {
            assertEquals(3, store.logEntries().size());
            assertEquals(2, store.chatLogs().size());
            assertEquals(List.of("first session"),
                    store.query(ChatQuery.all().withSubstring("first session"))
                            .stream().map(ChatEntry::message).toList());
            assertEquals(List.of("second session"),
                    store.query(ChatQuery.all().withSubstring("second session"))
                            .stream().map(ChatEntry::message).toList());
            assertEquals(List.of("first session", "shared later", "second session"),
                    store.logEntries().stream().map(ChatEntry::message).toList());
        }
    }

    @Test
    void sessionMessagesSurviveReopenAndStayQueryable() {
        Path database = database();
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);

        try (LogStore store = LogStore.open(database)) {
            store.startSession("26.2", startedAt);
            assertTrue(store.importSessionMessage("before", startedAt));
            assertTrue(store.importSessionMessage("the needle", startedAt.plusSeconds(1)));
            assertTrue(store.importSessionMessage("after", startedAt.plusSeconds(2)));
        }

        try (LogStore store = LogStore.open(database)) {
            assertEquals(1, store.chatLogs().size());
            ChatLog log = store.chatLogs().getFirst();
            assertInstanceOf(LogSource.Session.class, log.source());
            assertEquals("26.2", log.minecraftVersion());
            assertEquals(LocalDate.of(2026, 8, 26), log.date());
            assertEquals(startedAt, log.startTime());
            assertEquals(startedAt.plusSeconds(2), log.endTime());

            List<ChatEntry> withContext = store.query(ChatQuery.all()
                    .withSubstring("needle")
                    .withContextLines(1));
            assertEquals(List.of("before", "the needle", "after"),
                    withContext.stream().map(ChatEntry::message).toList());

            // The previous session is stored, but it is not still active on a freshly opened store.
            assertThrows(LogDataException.class, () -> store.importSessionMessage("more"));

            store.startSession("26.2", startedAt.plusHours(1));
            assertTrue(store.importSessionMessage("next session", startedAt.plusHours(1)));
        }

        try (LogStore store = LogStore.open(database)) {
            assertEquals(2, store.chatLogs().size());
            assertEquals(4, store.logEntries().size());
            assertEquals(1, store.query(ChatQuery.all().withSubstring("next session")).size());
            assertEquals(1, store.query(ChatQuery.all().withSubstring("the needle")).size());
        }
    }

    @Test
    void archiveImportsSurviveReopenAndRemainQueryable() throws IOException {
        Path database = database();
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
                "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "in archive", "archive needle"))));

        try (LogStore store = LogStore.open(database)) {
            ImportResult result = store.importArchive(archive);
            assertEquals(1, result.importedFiles());
            assertEquals(2, result.importedEntries());
        }

        try (LogStore store = LogStore.open(database)) {
            ChatEntry entry = store.query(ChatQuery.all().withSubstring("archive needle")).getFirst();
            LogSource.Archive source = assertInstanceOf(LogSource.Archive.class, entry.chatLog().source());
            assertEquals(archive.toAbsolutePath().normalize(), source.path());
            assertEquals("logs/2026-01-02-1.log.gz", source.entryPath());
            assertEquals("1.21.8", entry.chatLog().minecraftVersion());
            assertEquals(LocalDate.of(2026, 1, 2), entry.chatLog().date());

            List<ChatEntry> withContext = store.query(ChatQuery.all()
                    .withSubstring("archive needle")
                    .withContextLines(1));
            assertEquals(List.of("in archive", "archive needle"),
                    withContext.stream().map(ChatEntry::message).toList());
        }
    }

    @Test
    void unicodeMessagesSurviveReopen() throws IOException {
        Path database = database();
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-24-1.log.gz",
                LogFixtures.modernLog("26.2", "hello 世界", "café", "emoji \uD83C\uDFAE"));

        try (LogStore store = LogStore.open(database)) {
            store.importDirectory(tempDir);
        }

        try (LogStore store = LogStore.open(database)) {
            assertEquals("hello 世界", store.query(ChatQuery.all().withSubstring("世界")).getFirst().message());
            assertEquals("café", store.query(ChatQuery.all().withSubstring("café")).getFirst().message());
            assertEquals("emoji \uD83C\uDFAE",
                    store.query(ChatQuery.all().withSubstring("\uD83C\uDFAE")).getFirst().message());
        }
    }
}
