package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Serialises every {@link LogStore} call onto one worker thread. The store is not safe for concurrent use, and
 * imports plus queries must not run on the Minecraft client thread. Live chat that arrives before
 * {@link #startSession(String, String)} is queued and flushed when the session starts, so boot import
 * cannot drop those lines.
 */
public final class LogStoreWorker implements AutoCloseable {
    private final ExecutorService executor;
    private final AtomicBoolean cancelImport = new AtomicBoolean();
    private final Queue<PendingLiveMessage> pendingLive = new ArrayDeque<>();
    private volatile LogStore store;
    private boolean sessionStarted;

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
            flushPendingLive();
            return log;
        });
    }

    /**
     * Queues a live chat line stamped with the player and server/world read on the client thread.
     * Returns immediately; the insert runs on the worker.
     */
    public void importSessionMessage(Component message, String minecraftUser, String serverOrWorld) {
        FormattingCodes.Parsed flat = ComponentFormatting.flatten(message);
        String text = flat.text();
        long[] formatting = flat.formatting() == null ? null : flat.formatting().clone();
        String user = minecraftUser == null || minecraftUser.isBlank() ? null : minecraftUser;
        String place = serverOrWorld == null || serverOrWorld.isBlank() ? null : serverOrWorld;
        executor.execute(() -> {
            PendingLiveMessage pending = new PendingLiveMessage(text, formatting, user, place);
            if (store == null || !sessionStarted) {
                pendingLive.add(pending);
                return;
            }
            writeLive(pending);
        });
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
            submit(this::touchSessionEndTimeNow).join();
            submit(this::closeStore).join();
        } catch (CompletionException ignored) {
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
        pendingLive.clear();
        sessionStarted = false;
        if (store != null) {
            store.close();
            store = null;
        }
    }

    private void flushPendingLive() {
        PendingLiveMessage pending;
        while ((pending = pendingLive.poll()) != null) {
            writeLive(pending);
        }
    }

    private void writeLive(PendingLiveMessage pending) {
        if (store == null) return;
        try {
            store.importSessionMessage(pending.text, pending.formatting, pending.minecraftUser,
                pending.serverOrWorld);
        } catch (LogDataException e) {
            AllTheLogsClient.LOGGER.warn("Could not store a live chat line", e);
        }
    }

    private record PendingLiveMessage(String text, long[] formatting, String minecraftUser, String serverOrWorld) {
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
