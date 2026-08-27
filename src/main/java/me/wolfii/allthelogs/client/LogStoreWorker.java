package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serialises every {@link LogStore} call onto one worker thread. The store is not safe for concurrent use, and
 * imports plus queries must not run on the Minecraft client thread.
 */
public final class LogStoreWorker implements AutoCloseable {
    private final ExecutorService executor;
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private LogStore store;

    public LogStoreWorker() {
        this.executor = Executors.newSingleThreadExecutor(daemonFactory());
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "allthelogs-store");
            thread.setDaemon(true);
            return thread;
        };
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

    public CompletableFuture<ChatLog> startSession(String minecraftVersion) {
        return submit(() -> requireStore().startSession(minecraftVersion));
    }

    /**
     * Queues a live chat line. Returns immediately; the insert runs on the worker.
     */
    public void importSessionMessage(Component message) {
        FormattingCodes.Parsed flat = ComponentFormatting.flatten(message);
        String text = flat.text();
        long[] formatting = flat.formatting() == null ? null : flat.formatting().clone();
        executor.execute(() -> {
            if (store == null) return;
            store.importSessionMessage(text, formatting);
        });
    }

    public void importSessionMessage(String message) {
        String copy = Objects.requireNonNull(message, "message");
        executor.execute(() -> {
            if (store == null) return;
            store.importSessionMessage(copy);
        });
    }

    public CompletableFuture<List<ChatEntry>> query(ChatQuery query) {
        ChatQuery copy = Objects.requireNonNull(query, "query");
        return submit(() -> requireStore().query(copy));
    }

    public CompletableFuture<MatchSummary> summarize(ChatQuery query) {
        ChatQuery copy = Objects.requireNonNull(query, "query");
        return submit(() -> requireStore().summarize(copy));
    }

    public CompletableFuture<List<ChatEntry>> around(ChatLog log, int lineIndex, int radius) {
        return around(log, lineIndex, radius, radius);
    }

    public CompletableFuture<List<ChatEntry>> around(ChatLog log, int lineIndex, int before, int after) {
        ChatLog copy = Objects.requireNonNull(log, "log");
        int beforeLines = Math.max(0, before);
        int afterLines = Math.max(0, after);
        return submit(() -> requireStore().around(copy, lineIndex, beforeLines, afterLines));
    }

    public CompletableFuture<LogStoreMetadata> metadata() {
        return submit(() -> requireStore().metadata());
    }

    public boolean isOpen() {
        return store != null;
    }

    @Override
    public void close() {
        try {
            submit(this::closeStore).join();
        } catch (CompletionException ignored) {
            closeStore();
        } finally {
            executor.shutdownNow();
        }
    }

    private LogStore requireStore() {
        if (store == null) {
            throw new IllegalStateException("log store is not open");
        }
        return store;
    }

    private void closeStore() {
        if (store != null) {
            store.close();
            store = null;
        }
    }

    private CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
}
