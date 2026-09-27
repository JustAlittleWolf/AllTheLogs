package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.api.PendingImportMessage;
import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.store.StoreCancellation;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serialises every {@link LogStore} call onto one worker thread. The store is not safe for concurrent use, and
 * imports plus queries must not run on the Minecraft client thread. Live chat is queued as it is captured.
 * Once a session is active, {@link #flushQueuedLiveMessages()} writes the whole queue in one
 * {@link LogStore#importSessionMessages} call; the client does that once per tick, and only when the queue
 * is not empty. Lines that arrive before {@link #startSession(String, String)} stay queued until the session
 * starts, so boot import cannot drop them. Capture time is recorded when the line is queued, not when DuckDB
 * inserts it.
 */
public final class LogStoreWorker implements AutoCloseable {
    private final ExecutorService executor;
    private final StoreCancellation cancellation = StoreCancellation.create();
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private final AtomicBoolean liveFlushScheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Queue<PendingImportMessage> pendingLive = new ConcurrentLinkedQueue<>();
    private volatile LogStore store;
    private volatile boolean sessionStarted;
    private volatile Thread workerThread;

    public LogStoreWorker() {
        this.executor = Executors.newSingleThreadExecutor(daemonFactory());
    }

    private ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "allthelogs-store");
            thread.setDaemon(true);
            workerThread = thread;
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

    public CompletableFuture<ChatLog> startSession(String minecraftVersion, String minecraftUser) {
        return submit(() -> {
            ChatLog log = requireStore().startSession(minecraftVersion, minecraftUser);
            sessionStarted = true;
            storeQueuedLive();
            return log;
        });
    }

    /**
     * Queues a live chat line stamped with the player, server/world, and clock time from this call.
     * Returns immediately. The line is written on the next {@link #flushQueuedLiveMessages()} once a
     * session is active, or when that session starts, so a burst does not enqueue one store call per line.
     */
    public void importSessionMessage(Component message, String minecraftUser, String serverOrWorld) {
        LocalDateTime capturedAt = LocalDateTime.now();
        FormattingCodes.Parsed flat = ComponentFormatting.flatten(message);
        String user = minecraftUser == null || minecraftUser.isBlank() ? null : minecraftUser;
        String place = serverOrWorld == null || serverOrWorld.isBlank() ? null : serverOrWorld;
        pendingLive.add(new PendingImportMessage(flat.text(), flat.formatting(), user, place, capturedAt));
    }

    /**
     * Writes every queued live line in one store call when a session is active and the queue is not empty.
     * No-op otherwise, and no-op when a flush is already waiting on the worker. Meant to be called once
     * per client tick.
     */
    public void flushQueuedLiveMessages() {
        if (!sessionStarted || pendingLive.isEmpty()) return;
        if (!liveFlushScheduled.compareAndSet(false, true)) return;
        try {
            executor.execute(this::writeScheduledLive);
        } catch (RejectedExecutionException e) {
            liveFlushScheduled.set(false);
        }
    }

    /**
     * Advances the live session's end time to now on the worker thread. No-op when the store is not open
     * or no session is active.
     */
    public void touchSessionEndTime() {
        try {
            executor.execute(this::touchSessionEndTimeNow);
        } catch (RejectedExecutionException ignored) {
            // The worker is already shut down with the game.
        }
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

    public CompletableFuture<List<ChatEntry>> matchingContextToward(ChatLog log, int lineIndex, boolean olderInFile,
                                                                    int limit, me.wolfii.allthelogs.api.ChatQuery query,
                                                                    LocalDate day) {
        ChatLog copy = Objects.requireNonNull(log, "log");
        int cap = Math.max(0, limit);
        var filter = query;
        var stayOn = day;
        return submit(() -> requireStore().matchingContextToward(copy, lineIndex, olderInFile, cap, filter, stayOn));
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

    public CompletableFuture<LogStoreMetadata> browserMetadata() {
        return submit(() -> requireStore().browserMetadata());
    }

    public CompletableFuture<Optional<Path>> databasePath() {
        return submit(() -> requireStore().databasePath());
    }

    /**
     * Runs {@code task} on the store thread. Tests use this to hold the worker inside a query and
     * then {@link #close()} it.
     */
    CompletableFuture<Void> run(Runnable task) {
        return submit(task);
    }

    /**
     * Stops the worker without waiting out a schema migration or import. Cancels the running
     * DuckDB statement first, so native query threads exit instead of keeping the process up
     * after the game window is gone. The database file is left as the last committed statement.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        cancelImport.set(true);
        cancellation.requestStop();
        Thread worker = workerThread;
        if (worker != null) worker.interrupt();
        try {
            Future<?> stopping = executor.submit(() -> {
                Thread.interrupted();
                storeQueuedLive();
                touchSessionEndTimeNow();
                closeStore();
            });
            stopping.get(15, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            closeStore();
        } catch (TimeoutException e) {
            AllTheLogsClient.LOGGER.warn("The log store was still busy while the game was closing");
        } catch (ExecutionException e) {
            if (!StoreCancellation.isClosedRequest(e.getCause())) {
                AllTheLogsClient.LOGGER.warn("Could not close the log store", e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    AllTheLogsClient.LOGGER.warn("The log store thread did not stop");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private LogStore requireStore() {
        if (store == null) {
            throw new IllegalStateException("log store is not open");
        }
        return store;
    }

    private void closeStore() {
        storeQueuedLive();
        pendingLive.clear();
        sessionStarted = false;
        if (store != null) {
            store.close();
            store = null;
        }
    }

    private void writeScheduledLive() {
        try {
            storeQueuedLive();
        } finally {
            liveFlushScheduled.set(false);
        }
    }

    private void storeQueuedLive() {
        if (store == null || !sessionStarted) return;
        List<PendingImportMessage> batch = drainPendingLive();
        if (batch.isEmpty()) return;
        try {
            store.importSessionMessages(batch);
        } catch (LogDataException e) {
            AllTheLogsClient.LOGGER.warn("Could not store live chat lines", e);
        }
    }

    private List<PendingImportMessage> drainPendingLive() {
        List<PendingImportMessage> batch = new ArrayList<>();
        PendingImportMessage pending;
        while ((pending = pendingLive.poll()) != null) {
            batch.add(pending);
        }
        return batch;
    }

    private void touchSessionEndTimeNow() {
        if (store == null) return;
        try {
            store.updateSessionEndTime(LocalDateTime.now());
        } catch (LogDataException ignored) {
        }
    }

    private CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(onWorker(task), executor);
    }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            StoreCancellation.install(cancellation);
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                StoreCancellation.clear();
            }
        }, executor);
    }

    private Runnable onWorker(Runnable task) {
        return () -> {
            StoreCancellation.install(cancellation);
            try {
                task.run();
            } finally {
                StoreCancellation.clear();
            }
        };
    }
}
