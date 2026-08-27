package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.store.SessionMarker;
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

/**
 * End to end coverage of the advertised portable file: write a database, close it, open it again, and query.
 */
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
        LogFixtures.writePlain(logs, "debug.log", LogFixtures.legacyLog("zeta", "another needle"));
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
            assertEquals(8, store.allEntries().size());
            assertEquals(3, store.chatLogs().size());

            assertEquals(2, store.findEntries(ChatQuery.all().withSubstring("NEEDLE")).size());
            assertEquals(0, store.findEntries(ChatQuery.all().withSubstringCaseSensitive("NEEDLE")).size());

            List<ChatEntry> regex = store.findEntries(ChatQuery.all().withRegex("^(alpha|gamma)$"));
            assertEquals(List.of("alpha", "gamma"), regex.stream().map(ChatEntry::message).toList());

            List<ChatEntry> range = store.findEntries(ChatQuery.all()
                    .startingAt(LocalDateTime.of(2026, 8, 25, 0, 0))
                    .upUntil(LocalDateTime.of(2026, 8, 26, 0, 0)));
            assertEquals(List.of("delta", "needle in here", "epsilon"),
                    range.stream().map(ChatEntry::message).toList());

            List<ChatEntry> combined = store.findEntries(ChatQuery.all()
                    .withSubstring("needle")
                    .startingAt(LocalDateTime.of(2026, 8, 25, 0, 0))
                    .upUntil(LocalDateTime.of(2026, 8, 26, 0, 0)));
            assertEquals(List.of("needle in here"), combined.stream().map(ChatEntry::message).toList());

            List<ChatEntry> withContext = store.findEntries(ChatQuery.all()
                    .withSubstring("needle in here")
                    .withContextLines(1));
            assertEquals(List.of("delta", "needle in here", "epsilon"),
                    withContext.stream().map(ChatEntry::message).toList());

            ChatEntry needle = store.findEntries(ChatQuery.all().withSubstring("needle in here")).getFirst();
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
            assertEquals(3, store.allEntries().size());
            assertEquals(2, store.chatLogs().size());
            assertEquals(List.of("first session"),
                    store.findEntries(ChatQuery.all().withSubstring("first session"))
                            .stream().map(ChatEntry::message).toList());
            assertEquals(List.of("second session"),
                    store.findEntries(ChatQuery.all().withSubstring("second session"))
                            .stream().map(ChatEntry::message).toList());
            assertEquals(List.of("first session", "shared later", "second session"),
                    store.allEntries().stream().map(ChatEntry::message).toList());
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
            assertTrue(SessionMarker.isId(((LogSource.Session) log.source()).id()));
            assertEquals("26.2", log.minecraftVersion());
            assertEquals(LocalDate.of(2026, 8, 26), log.date());
            assertEquals(startedAt, log.startTime());
            assertEquals(startedAt.plusSeconds(2), log.endTime());

            List<ChatEntry> withContext = store.findEntries(ChatQuery.all()
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
            assertEquals(4, store.allEntries().size());
            assertEquals(1, store.findEntries(ChatQuery.all().withSubstring("next session")).size());
            assertEquals(1, store.findEntries(ChatQuery.all().withSubstring("the needle")).size());
        }
    }

    @Test
    void sessionIdSurvivesReopenAndSkipsTheMatchingLogFile() throws IOException {
        Path database = database();
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);
        String sessionId;
        try (LogStore store = LogStore.open(database)) {
            ChatLog session = store.startSession("26.2", startedAt);
            sessionId = ((LogSource.Session) session.source()).id();
            assertTrue(store.importSessionMessage("live", startedAt));
        }

        String log = "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n"
            + "[10:00:02] [allthelogs-store/INFO]: " + SessionMarker.message(sessionId) + "\n"
            + "[10:00:10] [Render thread/INFO]: [CHAT] live\n"
            + "[10:00:11] [Render thread/INFO]: [CHAT] only in the file\n";
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-26-1.log.gz", log);

        try (LogStore store = LogStore.open(database)) {
            assertEquals(sessionId, ((LogSource.Session) store.chatLogs().getFirst().source()).id());
            ImportResult result = store.importDirectory(tempDir);
            assertEquals(0, result.importedFiles());
            assertEquals(1, result.skippedFiles());
            assertEquals(List.of("live"), store.allEntries().stream().map(ChatEntry::message).toList());
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
            ChatEntry entry = store.findEntries(ChatQuery.all().withSubstring("archive needle")).getFirst();
            LogSource.Archive source = assertInstanceOf(LogSource.Archive.class, entry.chatLog().source());
            assertEquals(archive.toAbsolutePath().normalize(), source.path());
            assertEquals("logs/2026-01-02-1.log.gz", source.entryPath());
            assertEquals("1.21.8", entry.chatLog().minecraftVersion());
            assertEquals(LocalDate.of(2026, 1, 2), entry.chatLog().date());

            List<ChatEntry> withContext = store.findEntries(ChatQuery.all()
                    .withSubstring("archive needle")
                    .withContextLines(1));
            assertEquals(List.of("in archive", "archive needle"),
                    withContext.stream().map(ChatEntry::message).toList());
        }
    }

    @Test
    void createsMissingParentDirectories() {
        Path database = tempDir.resolve("nested/.allthelogs/logs.duckdb");
        try (LogStore store = LogStore.open(database)) {
            assertEquals(database.toAbsolutePath().normalize(), store.databasePath().orElseThrow());
        }
        assertTrue(Files.isDirectory(database.getParent()));
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
            assertEquals("hello 世界", store.findEntries(ChatQuery.all().withSubstring("世界")).getFirst().message());
            assertEquals("café", store.findEntries(ChatQuery.all().withSubstring("café")).getFirst().message());
            assertEquals("emoji \uD83C\uDFAE",
                    store.findEntries(ChatQuery.all().withSubstring("\uD83C\uDFAE")).getFirst().message());
        }
    }

    @Test
    void importCompactsTheFileAndKeepsAnActiveSessionWritable() throws IOException {
        Path database = database();
        Path root = instanceLogs();
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0, 0);

        try (LogStore store = LogStore.open(database)) {
            store.startSession("26.2", startedAt);
            assertTrue(store.importSessionMessage("live before import", startedAt));

            ImportResult result = store.importDirectory(root);
            assertEquals(3, result.importedFiles());
            assertTrue(store.importSessionMessage("live after compact", startedAt.plusSeconds(5)));

            assertEquals(9, store.allEntries().size());
            assertEquals(List.of("live before import"),
                store.findEntries(ChatQuery.all().withSubstring("live before import"))
                    .stream().map(ChatEntry::message).toList());
            assertEquals(List.of("live after compact"),
                store.findEntries(ChatQuery.all().withSubstring("live after compact"))
                    .stream().map(ChatEntry::message).toList());
        }

        try (LogStore store = LogStore.open(database)) {
            assertEquals(9, store.allEntries().size());
            assertTrue(store.allEntries().stream().map(ChatEntry::message).toList()
                .containsAll(List.of("alpha", "live before import", "live after compact")));
        }
    }
}
