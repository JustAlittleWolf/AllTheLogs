package me.wolfii.allthelogs.data;

/**
 * Live snapshot of an import run, delivered to the callback passed to
 * {@link LogStore#importDirectory(java.nio.file.Path, ImportOptions, java.util.function.Consumer)} and
 * {@link LogStore#importArchive(java.nio.file.Path, ImportOptions, java.util.function.Consumer)}.
 * <p>
 * Until {@link #discoveryComplete} is true, {@link #discoveredFiles} is still growing, so {@link #fraction()} is only
 * a lower bound on overall progress. The callback may be invoked from worker threads; calls are never concurrent. It
 * should return quickly. Session capture ({@link LogStore#startSession(String)},
 * {@link LogStore#importSessionMessage(String)}) does not report progress.
 *
 * @param completedFiles     log files already imported (including empty), skipped, or failed
 * @param discoveredFiles    log files found so far; the final total once {@link #discoveryComplete} is true
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
    boolean discoveryComplete,
    LogSource current
) {
    /**
     * Completed files as a fraction of those discovered so far, in {@code [0, 1]}. Zero if nothing has been discovered
     * yet.
     */
    public double fraction() {
        return discoveredFiles == 0 ? 0d : (double) completedFiles / discoveredFiles;
    }
}
