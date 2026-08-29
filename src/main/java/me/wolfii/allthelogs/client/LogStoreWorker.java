package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.DaemonThreads;
import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serialises every {@link LogStore} call onto one worker thread. The store is not safe for concurrent use, and
 * imports plus queries must not run on the Minecraft client thread.
 */
public final class LogStoreWorker implements AutoCloseable {
    private static final long CLOSE_WAIT_MS = 3_000L;

    private final ExecutorService executor;
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile LogStore store;

    public LogStoreWorker() {
        this.executor = Executors.newSingleThreadExecutor(runnable ->
            DaemonThreads.create("allthelogs-store", runnable));
    }

    public CompletableFuture<Void> open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        return submit(() -> {
            closeStore();
            store = LogStore.open(databasePath);
        });
    }

    public CompletableFuture<ImportResult> importDirectory(Path directory, ImportOptions options,
                                                           Consumer<ImportProgress> progress) {
        cancelImport.set(false);
        return submit(() -> requireStore().importDirectory(directory, options, progress, cancelImport::get));
    }

    public CompletableFuture<ImportResult> importArchive(Path archive, ImportOptions options,
                                                         Consumer<ImportProgress> progress) {
        cancelImport.set(false);
        return submit(() -> requireStore().importArchive(archive, options, progress, cancelImport::get));
    }

    public void cancelImport() {
        cancelImport.set(true);
    }

    public CompletableFuture<ChatLog> startSession(String minecraftVersion, String minecraftUser) {
        return submit(() -> requireStore().startSession(minecraftVersion, minecraftUser));
    }

    /**
     * Queues a live chat line. Returns immediately; the insert runs on the worker.
     */
    public void importSessionMessage(Component message) {
        FormattingCodes.Parsed flat = ComponentFormatting.flatten(message);
        String text = flat.text();
        long[] formatting = flat.formatting() == null ? null : flat.formatting().clone();
        execute(() -> {
            if (store == null) return;
            store.importSessionMessage(text, formatting);
        });
    }

    /**
     * Advances the live session's end time to now on the worker thread. No-op when the store is not open
     * or no session is active.
     */
    public void touchSessionEndTime() {
        execute(this::touchSessionEndTimeNow);
    }

    public boolean isOpen() {
        return store != null;
    }

    public CompletableFuture<List<ChatEntry>> findEntries(me.wolfii.allthelogs.api.ChatQuery query) {
        var copy = Objects.requireNonNull(query, "query");
        return submit(() -> requireStore().findEntries(copy));
    }

    public CompletableFuture<MatchSummary> summarizeMatches(me.wolfii.allthelogs.api.ChatQuery query) {
        var copy = Objects.requireNonNull(query, "query");
        return submit(() -> requireStore().summarizeMatches(copy));
    }

    public CompletableFuture<Long> countMatches(me.wolfii.allthelogs.api.ChatQuery query) {
        var copy = Objects.requireNonNull(query, "query");
        return submit(() -> requireStore().countMatches(copy));
    }

    public CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int before, int after) {
        ChatLog copy = Objects.requireNonNull(log, "log");
        int beforeLines = Math.max(0, before);
        int afterLines = Math.max(0, after);
        return submit(() -> requireStore().entriesAround(copy, lineIndex, beforeLines, afterLines));
    }

    public CompletableFuture<List<ChatEntry>> allEntries() {
        return submit(() -> requireStore().allEntries());
    }

    public CompletableFuture<List<ChatLog>> chatLogs() {
        return submit(() -> requireStore().chatLogs());
    }

    public CompletableFuture<LogStoreMetadata> metadata() {
        return submit(() -> requireStore().metadata());
    }

    public CompletableFuture<Optional<Path>> databasePath() {
        return submit(() -> requireStore().databasePath());
    }

    /**
     * Cancels an in-flight import, stops the worker, checkpoints the live session, and closes DuckDB
     * so Minecraft can exit. Waits are bounded: a stuck query must not freeze the client thread.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        cancelImport.set(true);
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS)) {
                AllTheLogsClient.LOGGER.warn("Log store worker did not stop in time");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        Thread closer = DaemonThreads.create("allthelogs-store-close", () -> {
            touchSessionEndTimeNow();
            closeStore();
        });
        closer.start();
        try {
            closer.join(CLOSE_WAIT_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private LogStore requireStore() {
        if (store == null) {
            throw new IllegalStateException("log store is not open");
        }
        return store;
    }

    private void closeStore() {
        LogStore open;
        synchronized (this) {
            open = store;
            store = null;
        }
        if (open == null) return;
        try {
            open.close();
        } catch (RuntimeException e) {
            AllTheLogsClient.LOGGER.warn("Failed to close the log store", e);
        }
    }

    private void touchSessionEndTimeNow() {
        if (store == null) return;
        try {
            store.updateSessionEndTime(LocalDateTime.now());
        } catch (LogDataException ignored) {
        }
    }

    private void execute(Runnable task) {
        if (closed.get()) return;
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private CompletableFuture<Void> submit(Runnable task) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("log store worker is closed"));
        }
        return submit(() -> {
            task.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("log store worker is closed"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
}
