package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.ChatEntry;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LogStoreBootTest {
    @TempDir
    Path temp;

    @Test
    void staysUnsettledUntilImportAndSessionFinishThenReleasesTheOverlay() throws Exception {
        Path instance = temp.resolve("game");
        Path logs = instance.resolve("logs");
        Files.createDirectories(logs);
        writeGzippedLog(logs.resolve("2026-01-02-1.log.gz"), "imported from boot");

        LogStoreBoot boot = new LogStoreBoot();
        assertFalse(boot.isSettled(), "loading overlay should hold before boot starts");

        try (LogStoreWorker worker = new LogStoreWorker()) {
            var started = boot.start(worker, temp.resolve("logs.duckdb"), instance, List.of(),
                "26.2", "Tester");
            assertFalse(boot.isSettled(), "loading overlay should hold while import and clustering run");
            assertNotNull(started.get(30, TimeUnit.SECONDS));
            assertTrue(boot.isSettled(), "loading overlay may fade only after startSession returns");

            List<String> messages = worker.allEntries().join().stream().map(ChatEntry::message).toList();
            assertTrue(messages.contains("imported from boot"));
            assertEquals(2, worker.chatLogs().join().size(),
                "imported log plus the live session started at the end of boot");
        }
    }

    @Test
    void aSecondStartIsANoOpAndDoesNotClearSettled() {
        LogStoreBoot boot = new LogStoreBoot();
        boot.markSettled();
        assertTrue(boot.start(null, temp.resolve("x"), temp, List.of(), "26.2", null).toCompletableFuture()
            .isDone());
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
        assertNull(boot.start(null, temp.resolve("x"), temp, List.of(), "26.2", null).join());
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
            importFuture.join();
            worker.startSession("26.2", "Tester").join();

            List<String> messages = worker.allEntries().join().stream().map(ChatEntry::message).toList();
            assertTrue(messages.contains("from file"));
            assertTrue(messages.contains("while importing"),
                "live lines queued during import must flush when the session starts");
        }
    }
}
