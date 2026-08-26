package me.wolfii.allthelogs.data;

/**
 * Live snapshot of an import run, delivered to the callback passed to
 * {@link LogStore#importDirectory(java.nio.file.Path, ImportOptions, java.util.function.Consumer)} and
 * {@link LogStore#importArchive(java.nio.file.Path, ImportOptions, java.util.function.Consumer)}.
 * <p>
 * Until {@link #discoveryComplete} is true, {@link #discoveredFiles} is still growing. {@link #estimatedFiles} is a
 * first-pass guess of how many files the run will find, so {@link #fraction()} can move before discovery finishes.
 * The callback may be invoked from worker threads; calls are never concurrent. It should return quickly. Session
 * capture ({@link LogStore#startSession(String)}, {@link LogStore#importSessionMessage(String)}) does not report
 * progress.
 *
 * @param completedFiles     log files already imported (including empty), skipped, or failed
 * @param discoveredFiles    log files found so far; the final total once {@link #discoveryComplete} is true
 * @param estimatedFiles     guessed file count used until discovery completes; ignored afterwards
 * @param discoveryComplete  whether every log file has been found
 * @param current            the log file or archive currently being processed, or {@code null} between items and after
 *                           the run has finished. A {@link LogSource.File} while a file on disk is being read. A
 *                           {@link LogSource.Archive} while an archive is being read; {@link LogSource.Archive#entryPath()}
 *                           is empty while the archive itself is being opened, then the path of the log (or nested
 *                           archive) inside it
 */
public record ImportProgress(
    int completedFiles,
    int discoveredFiles,
    int estimatedFiles,
    boolean discoveryComplete,
    LogSource current
) {
    /**
     * Completed files as a fraction of the current total, in {@code [0, 1]}. Until discovery finishes the
     * denominator is {@code max(estimatedFiles, discoveredFiles)} so the bar can start from a file-count
     * guess instead of sitting at 100% while discovery and parsing stay in lock-step.
     */
    public double fraction() {
        int total = discoveryComplete ? discoveredFiles : Math.max(estimatedFiles, discoveredFiles);
        return total == 0 ? 0d : (double) completedFiles / total;
    }
}
