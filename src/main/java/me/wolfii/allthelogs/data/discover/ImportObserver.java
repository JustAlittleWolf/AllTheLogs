package me.wolfii.allthelogs.data.discover;

import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogSource;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Tracks import progress and notifies the caller-supplied callback.
 * All mutations and callback invocations are serialised. When no callback was supplied, every method returns
 * immediately.
 */
public final class ImportObserver {
    private final Consumer<ImportProgress> callback;
    private int discoveredFiles;
    private int completedFiles;
    private boolean discoveryComplete;
    private LogSource current;

    public ImportObserver(Consumer<ImportProgress> callback) {
        this.callback = callback;
    }

    void workingOnArchive(Path archive, String entryPath) {
        setCurrent(new LogSource.Archive(archive, entryPath));
    }

    void fileStarted(LogSource source) {
        if (callback == null) return;
        synchronized (this) {
            current = source;
            discoveredFiles++;
            callback.accept(snapshot());
        }
    }

    /**
     * Records that the current log file has been imported, skipped, or failed.
     */
    public void fileCompleted() {
        fileCompleted(null);
    }

    /**
     * Records that a log file has been stored, and makes it the current item so the UI still has something to
     * show after discovery has finished.
     */
    public void fileCompleted(LogSource source) {
        if (callback == null) return;
        synchronized (this) {
            if (source != null) current = source;
            completedFiles++;
            callback.accept(snapshot());
        }
    }

    /**
     * Marks that no more log files will be found. Parsing and writing may still be in flight.
     */
    public void discoveryFinished() {
        if (callback == null) return;
        synchronized (this) {
            discoveryComplete = true;
            callback.accept(snapshot());
        }
    }

    /**
     * Clears the current item after every discovered file has been handled.
     */
    public void finished() {
        if (callback == null) return;
        synchronized (this) {
            current = null;
            discoveryComplete = true;
            callback.accept(snapshot());
        }
    }

    private void setCurrent(LogSource source) {
        if (callback == null) return;
        synchronized (this) {
            current = source;
            callback.accept(snapshot());
        }
    }

    private ImportProgress snapshot() {
        return new ImportProgress(completedFiles, discoveredFiles, discoveryComplete, current);
    }
}
