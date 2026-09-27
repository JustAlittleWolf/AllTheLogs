package me.wolfii.allthelogs.data.store;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Stop request for the store worker. Closing the game sets it from the client thread, which
 * cancels the statement the worker is running. DuckDB only notices that from
 * {@link Statement#cancel()}; interrupting the Java thread does not stop a native query, and
 * those native threads keep the process alive after the window is gone.
 */
public final class StoreCancellation {
    static final String CLOSED_MESSAGE = "log store closed";
    private static final ThreadLocal<StoreCancellation> CURRENT = new ThreadLocal<>();

    private volatile Statement active;
    private volatile boolean stopped;

    private StoreCancellation() {
    }

    public static StoreCancellation create() {
        return new StoreCancellation();
    }

    /**
     * Makes {@code cancellation} the one {@link #throwIfStopped()} and the backup copy see on
     * this thread. The store worker installs its own cancellation for each task.
     */
    public static void install(StoreCancellation cancellation) {
        CURRENT.set(cancellation);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static StoreCancellation current() {
        return CURRENT.get();
    }

    /**
     * Remembers {@code statement} so a later {@link #requestStop()} can cancel it. A stop that
     * already arrived cancels {@code statement} immediately.
     */
    public void bind(Statement statement) {
        active = statement;
        if (stopped) cancel(statement);
    }

    public void unbind(Statement statement) {
        if (active == statement) active = null;
    }

    /**
     * Asks the worker to stop and cancels its current statement, if it is inside a query.
     */
    public void requestStop() {
        stopped = true;
        cancel(active);
    }

    public boolean isStopped() {
        return stopped;
    }

    /**
     * {@code true} when this worker was asked to stop, or the worker thread is interrupted.
     */
    public static boolean stoppedOnThisThread() {
        if (Thread.currentThread().isInterrupted()) return true;
        StoreCancellation current = CURRENT.get();
        return current != null && current.stopped;
    }

    /**
     * Throws when {@link #stoppedOnThisThread()} is set. Used between native queries, which
     * {@link Statement#cancel()} already interrupts.
     */
    public static void throwIfStopped() throws SQLException {
        if (stoppedOnThisThread()) throw new SQLException(CLOSED_MESSAGE);
    }

    /**
     * {@code true} when {@code error} came from closing the store rather than a failed migration.
     */
    public static boolean isClosedRequest(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) continue;
            if (message.contains(CLOSED_MESSAGE) || message.contains("INTERRUPT")) return true;
        }
        return false;
    }

    private static void cancel(Statement statement) {
        if (statement == null) return;
        try {
            statement.cancel();
        } catch (SQLException ignored) {
            // The statement can already be finished or closed by the worker.
        }
    }
}
