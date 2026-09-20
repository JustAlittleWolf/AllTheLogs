package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.config.StartupLogImports;
import me.wolfii.allthelogs.data.ChatLog;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client startup after the DuckDB native library is on the classpath: open the store, import this
 * instance (and extra directories), then start the live session. Starting a session clusters the
 * unoptimized tail from the previous run, which is the remaining post-import work when the
 * startup import itself skipped a full rewrite.
 * <p>
 * The vanilla loading overlay stays up until {@link #isSettled()} is true so that clustering and
 * compacting finish before the title screen — the store worker is single-threaded, so that work
 * otherwise keeps queries and live chat blocked after the overlay has already faded.
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
     * Starts the boot pipeline at most once. Completes after import and {@code startSession}
     * (including tail clustering), then marks this boot settled even if a step failed.
     */
    public CompletableFuture<ChatLog> start(LogStoreWorker worker, Path database, Path gameDirectory,
                                            List<String> extraDirectories, String minecraftVersion,
                                            String minecraftUser) {
        if (worker == null) {
            markSettled();
            return CompletableFuture.completedFuture(null);
        }
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return worker.open(database)
            .thenCompose(ignored -> StartupLogImports.importOnBoot(worker, gameDirectory, extraDirectories))
            .thenCompose(ignored -> worker.startSession(minecraftVersion, minecraftUser))
            .whenComplete((log, error) -> markSettled());
    }
}
