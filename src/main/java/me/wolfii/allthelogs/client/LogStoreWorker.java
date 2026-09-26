package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.api.PendingImportMessage;
import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
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
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private final AtomicBoolean liveFlushScheduled = new AtomicBoolean();
    private final Queue<PendingImportMessage> pendingLive = new ConcurrentLinkedQueue<>();
    private volatile LogStore store;
    private volatile boolean sessionStarted;

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
        executor.execute(this::touchSessionEndTimeNow);
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

    @Override
    public void close() {
        try {
            submit(() -> {
                storeQueuedLive();
                touchSessionEndTimeNow();
            }).join();
            submit(this::closeStore).join();
        } catch (CompletionException ignored) {
            storeQueuedLive();
            touchSessionEndTimeNow();
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
