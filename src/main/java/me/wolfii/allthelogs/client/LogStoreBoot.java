package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.ChatLog;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client startup after the DuckDB native library is on the classpath: open the store and start the
 * live session. Directory import runs afterwards on the store worker and does not keep the vanilla
 * loading overlay up.
 * <p>
 * {@link #isSettled()} is true once the session exists (or startup failed), so the overlay can fade
 * while logs from this instance and extra folders are still being scanned. Live chat that arrives
 * during that import is queued on the worker with its capture time, as {@link LogStoreWorker} already
 * does for lines that beat {@link LogStoreWorker#startSession(String, String)}.
 */
public final class LogStoreBoot {
    private final AtomicBoolean settled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    public boolean isSettled() {
        return settled.get();
    }

    /**
     * Releases the loading overlay without opening the store. Used when DuckDB failed to load.
     */
    public void markSettled() {
        settled.set(true);
    }

    /**
     * Starts the boot pipeline at most once. Completes after the store is open and
     * {@code startSession} has returned (including tail clustering of the previous live run), then
     * marks this boot settled even if a step failed.
     */
    public CompletableFuture<ChatLog> start(LogStoreWorker worker, Path database, String minecraftVersion,
                                            String minecraftUser) {
        if (worker == null) {
            markSettled();
            return CompletableFuture.completedFuture(null);
        }
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return worker.open(database)
            .thenCompose(ignored -> worker.startSession(minecraftVersion, minecraftUser))
            .whenComplete((log, error) -> markSettled());
    }
}
