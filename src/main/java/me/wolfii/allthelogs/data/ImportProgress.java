package me.wolfii.allthelogs.data;

/**
 * Live snapshot of an import run, delivered to the callback passed to
 * {@link LogStore#importDirectory(java.nio.file.Path, ImportOptions, java.util.function.Consumer, java.util.function.BooleanSupplier)} and
 * {@link LogStore#importArchive(java.nio.file.Path, ImportOptions, java.util.function.Consumer, java.util.function.BooleanSupplier)}.
 * <p>
 * Until {@link #discoveryComplete} is true, {@link #discoveredFiles} is still growing. {@link #estimatedFiles} is a
 * first-pass guess of how many files the run will find, so {@link #fraction()} can move before discovery finishes.
 * After every discovered file has been handled, the run moves through {@link ImportPhase#CHUNKING} (rewrite
 * {@code chat_entry} into time-ordered row groups) and {@link ImportPhase#OPTIMIZING} (analyze, checkpoint,
 * compact the on-disk file). Those two phases together occupy 40% of the bar, with file import occupying 60%.
 * The callback may be invoked from worker threads; calls are never concurrent. It should return quickly. Session
 * capture ({@link LogStore#startSession(String, String)}, {@link LogStore#importSessionMessage(String, long[])}) does not report
 * progress.
 *
 * @param completedFiles     log files already imported (including empty), skipped, or failed
 * @param discoveredFiles    log files found so far; the final total once {@link #discoveryComplete} is true
 * @param estimatedFiles     guessed file count used until discovery completes; ignored afterwards
 * @param discoveryComplete  whether every log file has been found
 * @param current            the log file or archive currently being processed, or {@code null} between items and after
 *                           the import phase has finished. A {@link LogSource.File} while a file on disk is being read. A
 *                           {@link LogSource.Archive} while an archive is being read; {@link LogSource.Archive#entryPath()}
 *                           is empty while the archive itself is being opened, then the path of the log (or nested
 *                           archive) inside it
 * @param phase              which share of the overall bar this snapshot belongs to
 * @param phaseFraction      progress within {@link #phase}, in {@code [0, 1]}; ignored while {@code phase} is
 *                           {@link ImportPhase#IMPORT}, where file counts drive the bar
 */
public record ImportProgress(
    int completedFiles,
    int discoveredFiles,
    int estimatedFiles,
    boolean discoveryComplete,
    LogSource current,
    ImportPhase phase,
    double phaseFraction
) {
    /** Share of {@link #fraction()} used by file discovery, parse, and write. */
    public static final double IMPORT_SHARE = 0.60;
    /** Share used to rewrite {@code chat_entry} into time-ordered DuckDB row groups. */
    public static final double CHUNKING_SHARE = 0.20;
    /** Share used to analyze, checkpoint, and compact the database file. */
    public static final double OPTIMIZING_SHARE = 0.20;

    public ImportProgress(int completedFiles, int discoveredFiles, int estimatedFiles,
                          boolean discoveryComplete, LogSource current) {
        this(completedFiles, discoveredFiles, estimatedFiles, discoveryComplete, current, ImportPhase.IMPORT, 0d);
    }

    public ImportProgress {
        phase = phase == null ? ImportPhase.IMPORT : phase;
        phaseFraction = Math.clamp(phaseFraction, 0d, 1d);
    }

    /**
     * Snapshot of this file-count state in {@code phase}, with {@code current} cleared once import has finished.
     */
    public ImportProgress withPhase(ImportPhase phase, double phaseFraction) {
        LogSource shown = phase == ImportPhase.IMPORT ? current : null;
        return new ImportProgress(completedFiles, discoveredFiles, estimatedFiles, discoveryComplete, shown,
            phase, phaseFraction);
    }

    /**
     * Completed files as a fraction of the current total, in {@code [0, 1]}. Until discovery finishes the
     * denominator is {@code max(estimatedFiles, discoveredFiles)} so the bar can start from a file-count
     * guess instead of sitting at 100% while discovery and parsing stay in lock-step.
     */
    public double fileFraction() {
        int total = discoveryComplete ? discoveredFiles : Math.max(estimatedFiles, discoveredFiles);
        return total == 0 ? 0d : (double) completedFiles / total;
    }

    /**
     * Overall run progress in {@code [0, 1]}: 60% file import, 20% chunking, 20% optimization.
     */
    public double fraction() {
        return switch (phase) {
            case IMPORT -> IMPORT_SHARE * fileFraction();
            case CHUNKING -> IMPORT_SHARE + CHUNKING_SHARE * phaseFraction;
            case OPTIMIZING -> IMPORT_SHARE + CHUNKING_SHARE + OPTIMIZING_SHARE * phaseFraction;
        };
    }
}
