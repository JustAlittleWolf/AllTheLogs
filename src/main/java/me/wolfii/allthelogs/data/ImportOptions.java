package me.wolfii.allthelogs.data;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Tuning knobs for an import run. Create one via {@link #defaults()} and derive variants with the {@code with*}
 * methods.
 *
 * @param recursive            for directory imports, whether to descend into subdirectories; for archive imports,
 *                             whether to descend into directories of the archive
 * @param nestedArchives       whether archives found inside the imported tree (or inside the imported archive) are
 *                             opened and imported as well
 * @param pathMatcher          glob restricting which log files are considered, matched against the path of the file
 *                             relative to the import root, e.g. {@code **&#47;logs&#47;**}; {@code null} accepts everything
 * @param parallelism          number of log files parsed concurrently
 * @param skipAlreadyImported  whether files whose source and entry path are already present in the database are
 *                             skipped instead of replaced
 * @param optimize             whether a successful import of new files should cluster {@code chat_entry} and compact
 *                             the database file afterwards
 * @param timezone             timezone the timestamps inside the imported log files are expressed in; they are
 *                             converted to the JVM's default timezone for storage, so that files written in different
 *                             zones stay comparable. Passing the default timezone leaves the values unchanged
 */
public record ImportOptions(
    boolean recursive,
    boolean nestedArchives,
    String pathMatcher,
    int parallelism,
    boolean skipAlreadyImported,
    boolean optimize,
    ZoneId timezone
) {
    public ImportOptions {
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be at least 1, was " + parallelism);
        Objects.requireNonNull(timezone, "timezone");
    }

    /**
     * Glob relative to a {@code logs} directory. Matches rotated and debug logs in that folder and any
     * subdirectory; {@code latest.log} is still skipped by discovery because it is the live Minecraft log.
     */
    public static final String LOGS_DIRECTORY_MATCHER = "**/*.{log,log.gz}";

    /**
     * Glob relative to a Minecraft instance / game directory. Restricts discovery to {@code logs} folders so
     * resource packs, worlds, and other zips are not opened as archives.
     */
    public static final String GAME_DIRECTORY_MATCHER = "**/logs/**";

    /**
     * Defaults to a recursive import of nested archives, one parser per CPU, replacing already imported files,
     * clustering and compacting afterwards, and treating log timestamps as local time.
     */
    public static ImportOptions defaults() {
        return new ImportOptions(true, true, null, Runtime.getRuntime().availableProcessors(), false, true,
            ZoneId.systemDefault());
    }

    /**
     * Startup import of this instance's {@code logs} folder: recursive, no nested archives, skip files already
     * stored, only {@code .log} / {@code .log.gz} names, and skip post-import clustering and compact.
     */
    public static ImportOptions currentLogsDirectory() {
        return defaults()
            .withRecursive(true)
            .withNestedArchives(false)
            .withSkipAlreadyImported(true)
            .withOptimize(false)
            .withPathMatcher(LOGS_DIRECTORY_MATCHER);
    }

    /**
     * Import of a Minecraft instance / game directory: walk {@code **&#47;logs&#47;**} and do not open zips found
     * elsewhere in the tree. Skips post-import clustering and compact because this path is used for frequent local
     * refreshes.
     */
    public static ImportOptions currentGameDirectory() {
        return defaults()
            .withRecursive(true)
            .withNestedArchives(false)
            .withSkipAlreadyImported(true)
            .withOptimize(false)
            .withPathMatcher(GAME_DIRECTORY_MATCHER);
    }

    public ImportOptions withRecursive(boolean recursive) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    public ImportOptions withNestedArchives(boolean nestedArchives) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    /**
     * @param pathMatcher a glob such as {@code **&#47;logs&#47;**}, or {@code null} to accept every file
     */
    public ImportOptions withPathMatcher(String pathMatcher) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    public ImportOptions withParallelism(int parallelism) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    public ImportOptions withSkipAlreadyImported(boolean skipAlreadyImported) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    public ImportOptions withOptimize(boolean optimize) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    /**
     * Timezone the timestamps inside the imported logs are expressed in. Stored timestamps are converted from this
     * timezone to the JVM default timezone. Passing the default timezone, which is also what {@link #defaults()} uses,
     * leaves the values unchanged.
     */
    public ImportOptions withTimezone(ZoneId timezone) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            timezone);
    }

    /**
     * @param timezone an IANA timezone id such as {@code America/New_York}, or {@code UTC}
     */
    public ImportOptions withTimezone(String timezone) {
        return withTimezone(ZoneId.of(timezone));
    }
}
