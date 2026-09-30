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
 * {@link #isSettled()} is true once the session exists (or startup failed). The loading overlay
 * uses {@link #isOverlayReleased(boolean)} instead, so it can fade while the driver download screen
 * is up and still stay down for the store boot that follows. Directory import keeps running after
 * the session exists. Live chat that arrives
 * during that import is queued on the worker with its capture time, as {@link LogStoreWorker} already
 * does for lines that beat {@link LogStoreWorker#startSession(String, String)}.
 */
public final class LogStoreBoot {
    private final AtomicBoolean settled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean downloadPromptReleased = new AtomicBoolean();

    public boolean isSettled() {
        return settled.get();
    }

    /**
     * Whether the vanilla loading overlay may fade. Offering the driver download releases it, and
     * starting that download does not bring the overlay back. {@link #isSettled()} still waits for
     * the store.
     */
    public boolean isOverlayReleased(boolean awaitsDownload) {
        if (settled.get()) return true;
        if (awaitsDownload) {
            downloadPromptReleased.set(true);
            return true;
        }
        return downloadPromptReleased.get();
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
