package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.config.StartupLogImports;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LogStoreBootTest {
    @TempDir
    Path temp;

    @Test
    void releasesTheOverlayAfterTheSessionStartsWithoutImportingLogs() throws Exception {
        Path instance = temp.resolve("game");
        Path logs = instance.resolve("logs");
        Files.createDirectories(logs);
        writeGzippedLog(logs.resolve("2026-01-02-1.log.gz"), "imported from boot");

        LogStoreBoot boot = new LogStoreBoot();
        assertFalse(boot.isSettled(), "loading overlay should hold before boot starts");

        try (LogStoreWorker worker = new LogStoreWorker()) {
            var started = boot.start(worker, temp.resolve("logs.duckdb"), "26.2", "Tester");
            ChatLog session = started.get(30, TimeUnit.SECONDS);
            assertNotNull(session);
            assertTrue(boot.isSettled(), "loading overlay may fade once the live session exists");
            assertEquals(1, worker.chatLogs().join().size(),
                "boot should start the live session without scanning the instance logs folder");
            assertTrue(worker.allEntries().join().isEmpty());
        }
    }

    @Test
    void instanceLogsStillImportAfterTheOverlayIsReleased() throws Exception {
        Path instance = temp.resolve("game");
        Path logs = instance.resolve("logs");
        Files.createDirectories(logs);
        writeGzippedLog(logs.resolve("2026-01-02-1.log.gz"), "imported from boot");

        LogStoreBoot boot = new LogStoreBoot();
        try (LogStoreWorker worker = new LogStoreWorker()) {
            boot.start(worker, temp.resolve("logs.duckdb"), "26.2", "Tester").get(30, TimeUnit.SECONDS);
            assertTrue(boot.isSettled());

            StartupLogImports.importOnBoot(worker, instance, List.of()).get(30, TimeUnit.SECONDS);

            List<String> messages = worker.allEntries().join().stream().map(ChatEntry::message).toList();
            assertTrue(messages.contains("imported from boot"));
            assertEquals(2, worker.chatLogs().join().size(),
                "imported log plus the live session started before import");
        }
    }

    @Test
    void aSecondStartIsANoOpAndDoesNotClearSettled() {
        LogStoreBoot boot = new LogStoreBoot();
        boot.markSettled();
        assertTrue(boot.start(null, temp.resolve("x"), "26.2", null).isDone());
        assertTrue(boot.isSettled());
    }

    @Test
    void duckDbFailureReleasesTheOverlayWithoutOpeningAStore() {
        LogStoreBoot boot = new LogStoreBoot();
        boot.markSettled();
        assertTrue(boot.isSettled());
    }

    @Test
    void startWithANullWorkerSettlesImmediately() {
        LogStoreBoot boot = new LogStoreBoot();
        assertNull(boot.start(null, temp.resolve("x"), "26.2", null).join());
        assertTrue(boot.isSettled());
    }

    private static void writeGzippedLog(Path file, String chat) throws Exception {
        String body = """
            [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
            [10:00:10] [Render thread/INFO]: [CHAT] %s
            """.formatted(chat);
        try (var out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}

class LogStoreWorkerLiveQueueTest {
    @TempDir
    Path temp;

    @Test
    void liveChatArrivingDuringImportIsKeptUntilTheSessionStarts() throws Exception {
        Path logs = temp.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("2026-01-02-1.log"), """
            [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
            [10:00:10] [Render thread/INFO]: [CHAT] from file
            """);

        try (LogStoreWorker worker = new LogStoreWorker()) {
            worker.open(temp.resolve("logs.duckdb")).join();
            var importFuture = worker.importDirectory(logs, me.wolfii.allthelogs.data.ImportOptions.currentLogsDirectory(),
                null);
            worker.importSessionMessage(Component.literal("while importing"), "Tester", "world/overworld");
            LocalDateTime queuedAt = LocalDateTime.now();
            Thread.sleep(50);
            importFuture.join();
            worker.startSession("26.2", "Tester").join();

            List<ChatEntry> entries = worker.allEntries().join();
            List<String> messages = entries.stream().map(ChatEntry::message).toList();
            assertTrue(messages.contains("from file"));
            assertTrue(messages.contains("while importing"),
                "live lines queued during import must flush when the session starts");
            ChatEntry live = entries.stream().filter(entry -> entry.message().equals("while importing")).findFirst()
                .orElseThrow();
            assertFalse(live.timestamp().isAfter(queuedAt),
                "flushing after import must not move the timestamp past ingest: " + live.timestamp()
                    + " queued by " + queuedAt);
        }
    }

    @Test
    void liveChatKeepsTheCaptureTimeEvenWhenTheStoreWritesLater() throws Exception {
        try (LogStoreWorker worker = new LogStoreWorker()) {
            worker.open(temp.resolve("logs.duckdb")).join();
            worker.importSessionMessage(Component.literal("first"), "Tester", "world/overworld");
            Thread.sleep(50);
            worker.importSessionMessage(Component.literal("second"), "Tester", "world/overworld");
            worker.startSession("26.2", "Tester").join();

            Map<String, ChatEntry> byMessage = worker.allEntries().join().stream()
                .collect(Collectors.toMap(ChatEntry::message, Function.identity()));
            LocalDateTime first = byMessage.get("first").timestamp();
            LocalDateTime second = byMessage.get("second").timestamp();
            assertTrue(Duration.between(first, second).toMillis() >= 20,
                "queued live lines must keep the clock from ingest, not the later insert: first="
                    + first + " second=" + second);
        }
    }

    @Test
    void liveChatDuringAnImportAfterTheSessionHasStartedIsStoredWithCaptureTime() throws Exception {
        Path logs = temp.resolve("logs");
        Files.createDirectories(logs);
        for (int day = 1; day <= 20; day++) {
            Files.writeString(logs.resolve("2026-01-%02d-1.log".formatted(day)), """
                [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
                [10:00:10] [Render thread/INFO]: [CHAT] file %d
                """.formatted(day));
        }

        try (LogStoreWorker worker = new LogStoreWorker()) {
            worker.open(temp.resolve("logs.duckdb")).join();
            worker.startSession("26.2", "Tester").join();
            var importFuture = worker.importDirectory(logs, me.wolfii.allthelogs.data.ImportOptions.currentLogsDirectory(),
                null);
            worker.importSessionMessage(Component.literal("during background import"), "Tester", "world/overworld");
            LocalDateTime queuedAt = LocalDateTime.now();
            importFuture.get(30, TimeUnit.SECONDS);
            worker.flushQueuedLiveMessages();

            List<ChatEntry> entries = worker.allEntries().join();
            ChatEntry live = entries.stream()
                .filter(entry -> entry.message().equals("during background import"))
                .findFirst()
                .orElseThrow();
            assertFalse(live.timestamp().isAfter(queuedAt),
                "background import must not delay the stored capture time: " + live.timestamp()
                    + " queued by " + queuedAt);
        }
    }

    @Test
    void aFloodStaysQueuedUntilOneFlushWritesIt() throws Exception {
        try (LogStoreWorker worker = new LogStoreWorker()) {
            worker.open(temp.resolve("logs.duckdb")).join();
            worker.importSessionMessage(Component.literal("before session"), "Tester", "world/overworld");
            worker.flushQueuedLiveMessages();
            worker.startSession("26.2", "Tester").join();

            List<String> afterStart = worker.allEntries().join().stream().map(ChatEntry::message).toList();
            assertEquals(List.of("before session"), afterStart);

            for (int i = 0; i < 40; i++) {
                String place = i % 2 == 0 ? "alpha.net" : null;
                worker.importSessionMessage(Component.literal("line " + i), "Tester", place);
            }
            assertEquals(List.of("before session"),
                worker.allEntries().join().stream().map(ChatEntry::message).toList(),
                "lines captured after the session starts stay queued until a tick flushes them");

            worker.flushQueuedLiveMessages();
            List<ChatEntry> flushed = worker.allEntries().join();
            assertEquals(41, flushed.size());
            assertEquals("line 0", flushed.get(1).message());
            assertEquals("alpha.net", flushed.get(1).serverOrWorld());
            assertEquals("line 1", flushed.get(2).message());
            assertNull(flushed.get(2).serverOrWorld());
            assertEquals("line 39", flushed.getLast().message());
            assertEquals("Tester", flushed.getLast().minecraftUser());

            worker.flushQueuedLiveMessages();
            assertEquals(41, worker.allEntries().join().size());

            worker.importSessionMessage(Component.literal("after flush"), "Tester", "alpha.net");
            assertEquals(41, worker.allEntries().join().size());
            worker.flushQueuedLiveMessages();
            assertEquals("after flush", worker.allEntries().join().getLast().message());
        }
    }
}
