package me.wolfii.allthelogs.data;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Tuning knobs for an import run. Create one via {@link #defaults()} and derive variants with the {@code with*}
 * methods.
 *
 * @param recursive                      for directory imports, whether to descend into subdirectories; for archive
 *                                       imports, whether to descend into directories of the archive
 * @param nestedArchives                 whether archives found inside the imported tree (or inside the imported
 *                                       archive) are opened and imported as well
 * @param pathMatcher                    glob restricting which files are considered, matched against the path
 *                                       relative to the import root up to and including a zip (or other archive)
 *                                       file, e.g. {@code **&#47;logs&#47;**}. Archives found while walking a
 *                                       directory must themselves match; paths inside a matching archive are not
 *                                       required to match again. When the import root is an archive, the glob applies
 *                                       to entry paths inside it until a nested archive. {@code null} accepts
 *                                       everything
 * @param parallelism                    number of log files parsed concurrently
 * @param skipAlreadyImported            whether files already present in the database (same source and entry path,
 *                                       or the same SHA-256 of raw bytes) are not imported again; path matches are
 *                                       left unopened, identical copies at a new path are skipped after the bytes
 *                                       are read
 * @param optimize                       whether a successful import of new files should cluster {@code chat_entry} and
 *                                       compact the database file afterwards
 * @param optimizeIfImportedFilesExceed  when {@code optimize} is false, still cluster and compact if more than this
 *                                       many files were newly stored; {@code 0} disables that threshold
 * @param timezone                       timezone the timestamps inside the imported log files are expressed in; they
 *                                       are converted to the JVM's default timezone for storage, so that files written
 *                                       in different zones stay comparable. Passing the default timezone leaves the
 *                                       values unchanged
 */
public record ImportOptions(
    boolean recursive,
    boolean nestedArchives,
    String pathMatcher,
    int parallelism,
    boolean skipAlreadyImported,
    boolean optimize,
    int optimizeIfImportedFilesExceed,
    ZoneId timezone
) {
    /**
     * Glob relative to a {@code logs} directory. Matches rotated and debug logs in that folder and any
     * subdirectory; {@code latest.log} is still skipped by discovery because it is the live Minecraft log.
     */
    public static final String LOGS_DIRECTORY_MATCHER = "**/*.{log,log.gz}";
    /**
     * Glob relative to a Minecraft instance / game directory. Restricts discovery to {@code logs} folders, so
     * resource packs, worlds, and other zips outside those folders are not opened as archives.
     */
    public static final String GAME_DIRECTORY_MATCHER = "**/logs/**";
    /**
     * Startup import of the running instance compacting the database only when more than this many files
     * were newly stored. User-initiated imports still always optimize.
     */
    public static final int STARTUP_OPTIMIZE_AFTER_MORE_THAN = 15;

    public ImportOptions {
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be at least 1, was " + parallelism);
        if (optimizeIfImportedFilesExceed < 0) {
            throw new IllegalArgumentException(
                "optimizeIfImportedFilesExceed must be at least 0, was " + optimizeIfImportedFilesExceed);
        }
        Objects.requireNonNull(timezone, "timezone");
    }

    /**
     * Defaults to a recursive import of nested archives, one parser per CPU, replacing already imported files,
     * clustering and compacting afterwards, and treating log timestamps as local time.
     */
    public static ImportOptions defaults() {
        return new ImportOptions(true, true, null, Runtime.getRuntime().availableProcessors(), false, true, 0,
            ZoneId.systemDefault());
    }

    /**
     * Startup import of this instance's {@code logs} folder: recursive, no nested archives, skip files already
     * stored or considered, only {@code .log} / {@code .log.gz} names, and compact only when more than
     * {@link #STARTUP_OPTIMIZE_AFTER_MORE_THAN} files were newly stored.
     */
    public static ImportOptions currentLogsDirectory() {
        return defaults()
            .withRecursive(true)
            .withNestedArchives(false)
            .withSkipAlreadyImported(true)
            .withOptimize(false)
            .withOptimizeIfImportedFilesExceed(STARTUP_OPTIMIZE_AFTER_MORE_THAN)
            .withPathMatcher(LOGS_DIRECTORY_MATCHER);
    }

    /**
     * Import of a Minecraft instance / game directory: walk {@code **&#47;logs&#47;**} and do not open zips found
     * elsewhere in the tree. Same skip and compact rules as {@link #currentLogsDirectory()}.
     */
    public static ImportOptions currentGameDirectory() {
        return defaults()
            .withRecursive(true)
            .withNestedArchives(false)
            .withSkipAlreadyImported(true)
            .withOptimize(false)
            .withOptimizeIfImportedFilesExceed(STARTUP_OPTIMIZE_AFTER_MORE_THAN)
            .withPathMatcher(GAME_DIRECTORY_MATCHER);
    }

    public ImportOptions withRecursive(boolean recursive) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    public ImportOptions withNestedArchives(boolean nestedArchives) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    /**
     * @param pathMatcher a glob such as {@code **&#47;logs&#47;**}, or {@code null} to accept every file
     */
    public ImportOptions withPathMatcher(String pathMatcher) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    public ImportOptions withParallelism(int parallelism) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    public ImportOptions withSkipAlreadyImported(boolean skipAlreadyImported) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    public ImportOptions withOptimize(boolean optimize) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    public ImportOptions withOptimizeIfImportedFilesExceed(int optimizeIfImportedFilesExceed) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    /**
     * Timezone the timestamps inside the imported logs are expressed in. Stored timestamps are converted from this
     * timezone to the JVM default timezone. Passing the default timezone, which is also what {@link #defaults()} uses,
     * leaves the values unchanged.
     */
    public ImportOptions withTimezone(ZoneId timezone) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone);
    }

    /**
     * @param timezone an IANA timezone id such as {@code America/New_York}, or {@code UTC}
     */
    public ImportOptions withTimezone(String timezone) {
        return withTimezone(ZoneId.of(timezone));
    }

    /**
     * Whether this run should cluster and compact after {@code importedFiles} were newly stored.
     */
    public boolean shouldOptimize(int importedFiles) {
        if (importedFiles <= 0) return false;
        if (optimize) return true;
        return optimizeIfImportedFilesExceed > 0 && importedFiles > optimizeIfImportedFilesExceed;
    }
}
