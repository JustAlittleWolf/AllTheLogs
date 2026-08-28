package me.wolfii.allthelogs.api;

import me.wolfii.allthelogs.client.LogStoreWorker;
import me.wolfii.allthelogs.data.LogStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only queries against the AllTheLogs chat database.
 * <p>
 * Obtained from {@link AllTheLogs#database()} in a running game. There is no import, session
 * capture, optimize, or close on this type; those stay on this mod's internal store worker.
 *
 * @see AllTheLogs
 */
public final class LogDatabase {
    private static final String NOT_OPEN = "log store is not open";

    private final Queries queries;

    /**
     * Wraps an already-open store and runs queries on the calling thread.
     * Intended for tests; other mods should use {@link AllTheLogs#database()}.
     */
    LogDatabase(LogStore store) {
        this(new DirectQueries(Objects.requireNonNull(store, "store")));
    }

    private LogDatabase(Queries queries) {
        this.queries = queries;
    }

    static LogDatabase forWorker(LogStoreWorker worker) {
        if (worker == null) {
            return new LogDatabase(NotOpenQueries.INSTANCE);
        }
        return new LogDatabase(new WorkerQueries(worker));
    }

    /**
     * Whether the live store has been opened and not yet closed.
     * <p>
     * {@code false} during client startup, after shutdown, and when AllTheLogs is not loaded as a
     * client. Query methods then complete exceptionally with {@link IllegalStateException}.
     */
    public boolean isOpen() {
        return queries.isOpen();
    }

    /**
     * Returns every entry matching {@code query}, resolved into records with their chat log attached.
     *
     * @throws java.util.concurrent.CompletionException wrapping {@link IllegalStateException} if the
     *                                                    store is not open
     */
    public CompletableFuture<List<ChatEntry>> findEntries(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        return queries.findEntries(query);
    }

    /**
     * Unpaged match count, first/last timestamps, and per-day counts for {@code query}.
     */
    public CompletableFuture<MatchSummary> summarizeMatches(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        return queries.summarizeMatches(query);
    }

    /**
     * Number of matching entries for {@code query}. Honours offset and limit; ignores context lines.
     */
    public CompletableFuture<Long> countMatches(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        return queries.countMatches(query);
    }

    /**
     * Chat lines from {@code log} within {@code radius} of {@code lineIndex}, inclusive of the centre line.
     */
    public CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int radius) {
        return entriesAround(log, lineIndex, radius, radius);
    }

    /**
     * Chat lines from {@code log} between {@code lineIndex - before} and {@code lineIndex + after}.
     */
    public CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int before, int after) {
        Objects.requireNonNull(log, "log");
        return queries.entriesAround(log, lineIndex, before, after);
    }

    /**
     * Every stored entry, oldest first. Convenience for {@code findEntries(ChatQuery.all())}.
     */
    public CompletableFuture<List<ChatEntry>> allEntries() {
        return queries.allEntries();
    }

    /**
     * Every imported chat log, ordered by date.
     */
    public CompletableFuture<List<ChatLog>> chatLogs() {
        return queries.chatLogs();
    }

    /**
     * Distinct versions, date range, counts, and database size.
     */
    public CompletableFuture<LogStoreMetadata> metadata() {
        return queries.metadata();
    }

    /**
     * The file this store is backed by, or empty for an in-memory store.
     */
    public CompletableFuture<Optional<Path>> databasePath() {
        return queries.databasePath();
    }

    private static me.wolfii.allthelogs.data.ChatLog asStoreLog(ChatLog log) {
        if (log instanceof me.wolfii.allthelogs.data.ChatLog storeLog) {
            return storeLog;
        }
        throw new IllegalArgumentException("chat log must be returned by AllTheLogs");
    }

    private static List<ChatEntry> entries(List<? extends ChatEntry> entries) {
        return List.copyOf(entries);
    }

    private static List<ChatLog> logs(List<? extends ChatLog> logs) {
        return List.copyOf(logs);
    }

    private interface Queries {
        boolean isOpen();

        CompletableFuture<List<ChatEntry>> findEntries(ChatQuery query);

        CompletableFuture<MatchSummary> summarizeMatches(ChatQuery query);

        CompletableFuture<Long> countMatches(ChatQuery query);

        CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int before, int after);

        CompletableFuture<List<ChatEntry>> allEntries();

        CompletableFuture<List<ChatLog>> chatLogs();

        CompletableFuture<LogStoreMetadata> metadata();

        CompletableFuture<Optional<Path>> databasePath();
    }

    private static final class WorkerQueries implements Queries {
        private final LogStoreWorker worker;

        private WorkerQueries(LogStoreWorker worker) {
            this.worker = worker;
        }

        @Override
        public boolean isOpen() {
            return worker.isOpen();
        }

        @Override
        public CompletableFuture<List<ChatEntry>> findEntries(ChatQuery query) {
            return worker.findEntries(query).thenApply(LogDatabase::entries);
        }

        @Override
        public CompletableFuture<MatchSummary> summarizeMatches(ChatQuery query) {
            return worker.summarizeMatches(query).thenApply(summary -> (MatchSummary) summary);
        }

        @Override
        public CompletableFuture<Long> countMatches(ChatQuery query) {
            return worker.countMatches(query);
        }

        @Override
        public CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int before, int after) {
            return worker.entriesAround(asStoreLog(log), lineIndex, before, after).thenApply(LogDatabase::entries);
        }

        @Override
        public CompletableFuture<List<ChatEntry>> allEntries() {
            return worker.allEntries().thenApply(LogDatabase::entries);
        }

        @Override
        public CompletableFuture<List<ChatLog>> chatLogs() {
            return worker.chatLogs().thenApply(LogDatabase::logs);
        }

        @Override
        public CompletableFuture<LogStoreMetadata> metadata() {
            return worker.metadata().thenApply(metadata -> (LogStoreMetadata) metadata);
        }

        @Override
        public CompletableFuture<Optional<Path>> databasePath() {
            return worker.databasePath();
        }
    }

    private static final class DirectQueries implements Queries {
        private final LogStore store;

        private DirectQueries(LogStore store) {
            this.store = store;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public CompletableFuture<List<ChatEntry>> findEntries(ChatQuery query) {
            return complete(() -> entries(store.findEntries(query)));
        }

        @Override
        public CompletableFuture<MatchSummary> summarizeMatches(ChatQuery query) {
            return complete(() -> store.summarizeMatches(query));
        }

        @Override
        public CompletableFuture<Long> countMatches(ChatQuery query) {
            return complete(() -> store.countMatches(query));
        }

        @Override
        public CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int before, int after) {
            return complete(() -> entries(store.entriesAround(asStoreLog(log), lineIndex, before, after)));
        }

        @Override
        public CompletableFuture<List<ChatEntry>> allEntries() {
            return complete(() -> entries(store.allEntries()));
        }

        @Override
        public CompletableFuture<List<ChatLog>> chatLogs() {
            return complete(() -> logs(store.chatLogs()));
        }

        @Override
        public CompletableFuture<LogStoreMetadata> metadata() {
            return complete(store::metadata);
        }

        @Override
        public CompletableFuture<Optional<Path>> databasePath() {
            return complete(store::databasePath);
        }

        private static <T> CompletableFuture<T> complete(Callable<T> task) {
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    private static final class NotOpenQueries implements Queries {
        private static final NotOpenQueries INSTANCE = new NotOpenQueries();

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public CompletableFuture<List<ChatEntry>> findEntries(ChatQuery query) {
            return notOpen();
        }

        @Override
        public CompletableFuture<MatchSummary> summarizeMatches(ChatQuery query) {
            return notOpen();
        }

        @Override
        public CompletableFuture<Long> countMatches(ChatQuery query) {
            return notOpen();
        }

        @Override
        public CompletableFuture<List<ChatEntry>> entriesAround(ChatLog log, int lineIndex, int before, int after) {
            return notOpen();
        }

        @Override
        public CompletableFuture<List<ChatEntry>> allEntries() {
            return notOpen();
        }

        @Override
        public CompletableFuture<List<ChatLog>> chatLogs() {
            return notOpen();
        }

        @Override
        public CompletableFuture<LogStoreMetadata> metadata() {
            return notOpen();
        }

        @Override
        public CompletableFuture<Optional<Path>> databasePath() {
            return notOpen();
        }

        private static <T> CompletableFuture<T> notOpen() {
            return CompletableFuture.failedFuture(new IllegalStateException(NOT_OPEN));
        }
    }
}
