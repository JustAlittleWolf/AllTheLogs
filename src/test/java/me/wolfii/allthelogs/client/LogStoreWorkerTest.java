package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.ImportOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogStoreWorkerTest {
    @TempDir
    Path tempDir;

    @Test
    void closeStopsTheWorkerThread() throws Exception {
        LogStoreWorker worker = new LogStoreWorker();
        worker.open(tempDir.resolve("logs.duckdb")).join();
        worker.startSession("26.2", "tester").join();
        worker.close();
        assertNoLiveThreads("allthelogs-store");
        assertNoLiveThreads("allthelogs-store-close");
    }

    @Test
    void closeDoesNotWaitForABlockedImport() throws Exception {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("debug.log"), """
            [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
            [10:00:10] [Render thread/INFO]: [CHAT] hello
            """, StandardCharsets.UTF_8);

        LogStoreWorker worker = new LogStoreWorker();
        worker.open(tempDir.resolve("logs.duckdb")).join();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        worker.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false), progress -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(10, TimeUnit.SECONDS), "import never started");

        long startedAt = System.nanoTime();
        worker.close();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsedMs < 8_000, "close hung for " + elapsedMs + "ms");
        assertTrue(interrupted.get(), "import thread was not interrupted");
        assertNoLiveThreads("allthelogs-store");
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
        assertEquals(List.of(), live, () -> "threads still running: " + live);
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
