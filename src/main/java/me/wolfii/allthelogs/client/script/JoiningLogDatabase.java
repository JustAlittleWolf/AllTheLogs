package me.wolfii.allthelogs.client.script;

import me.wolfii.allthelogs.api.ChatEntry;
import me.wolfii.allthelogs.api.ChatLog;
import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.api.LogDatabase;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * LogDatabase methods that return futures, joined on the calling (script) thread so GraalJS can use
 * the public API without a translation server.
 */
final class JoiningLogDatabase {
    private final LogDatabase database;

    JoiningLogDatabase(LogDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public boolean isOpen() {
        return database.isOpen();
    }

    public List<ChatEntry> findEntries(ChatQuery query) {
        return join(database.findEntries(query));
    }

    public Object summarizeMatches(ChatQuery query) {
        return join(database.summarizeMatches(query));
    }

    public long countMatches(ChatQuery query) {
        return join(database.countMatches(query));
    }

    public List<ChatEntry> entriesAround(ChatLog log, int lineIndex, int radius) {
        return join(database.entriesAround(log, lineIndex, radius));
    }

    public List<ChatEntry> entriesAround(ChatLog log, int lineIndex, int before, int after) {
        return join(database.entriesAround(log, lineIndex, before, after));
    }

    public List<ChatEntry> allEntries() {
        return join(database.allEntries());
    }

    public List<ChatLog> chatLogs() {
        return join(database.chatLogs());
    }

    public Object metadata() {
        return join(database.metadata());
    }

    public Optional<Path> databasePath() {
        return join(database.databasePath());
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }
}
