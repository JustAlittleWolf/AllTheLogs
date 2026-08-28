package me.wolfii.allthelogs.api;

import me.wolfii.allthelogs.client.AllTheLogsClient;

/**
 * Entry point for other Fabric client mods that need to read AllTheLogs' chat database.
 * <p>
 * This API is read-only: it does not import logs, capture the live session, optimize, or close
 * the store. Queries run on AllTheLogs' store worker, not the Minecraft client thread.
 * {@snippet :
 * AllTheLogs.database().findEntries(ChatQuery.all().withSubstring("welcome"))
 *         .thenAccept(hits -> hits.forEach(entry ->
 *                 System.out.println(entry.timestamp() + " " + entry.message())));
 * }
 * <p>
 * The database is opened asynchronously during client startup. {@link LogDatabase#isOpen()} is
 * {@code false} until that finishes (and after the client shuts down). Queries issued while the
 * store is not ready complete exceptionally with {@link IllegalStateException}.
 */
public final class AllTheLogs {
    private AllTheLogs() {
    }

    /**
     * The live AllTheLogs database for this Minecraft client.
     * <p>
     * Safe to call before the client has finished starting: {@link LogDatabase#isOpen()} is then
     * {@code false} and query futures fail with {@link IllegalStateException} rather than blocking.
     */
    public static LogDatabase database() {
        return LogDatabase.forWorker(AllTheLogsClient.worker());
    }
}
