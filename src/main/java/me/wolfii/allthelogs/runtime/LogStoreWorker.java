package me.wolfii.allthelogs.runtime;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatQuery;
import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogStore;
import me.wolfii.allthelogs.data.StoreMetadata;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Serialises every {@link LogStore} call onto one worker thread. The store is not safe for concurrent use, and
 * imports plus queries must not run on the Minecraft client thread.
 */
public final class LogStoreWorker implements AutoCloseable {
    private final ExecutorService executor;
    private LogStore store;

    public LogStoreWorker() {
        this.executor = Executors.newSingleThreadExecutor(daemonFactory());
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
        return submit(() -> requireStore().importDirectory(directory, options, progress));
    }

    public CompletableFuture<ImportResult> importArchive(Path archive, ImportOptions options,
                                                         Consumer<ImportProgress> progress) {
        return submit(() -> requireStore().importArchive(archive, options, progress));
    }

    public CompletableFuture<Void> startSession(String minecraftVersion) {
        return submit(() -> {
            requireStore().startSession(minecraftVersion);
        });
    }

    /**
     * Queues a live chat line. Returns immediately; the insert runs on the worker.
     */
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

    public CompletableFuture<List<LocalDateTime>> matchTimestamps(ChatQuery query) {
        return query(query).thenApply(entries -> entries.stream().map(ChatEntry::timestamp).toList());
    }

    public CompletableFuture<StoreMetadata> metadata() {
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

    private <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "allthelogs-store");
            thread.setDaemon(true);
            return thread;
        };
    }
}
