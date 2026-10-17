package me.wolfii.allthelogs.data.importer;

import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;

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
 * @param optimize                       whether a successful import or metadata update should rewrite the whole
 *                                       {@code chat_entry} table oldest-first and compact the database file afterwards
 * @param optimizeIfImportedFilesExceed  when {@code optimize} is false, still cluster if more than this many files
 *                                       were stored or updated. New-file imports can cluster only the appended
 *                                       files; metadata updates rewrite the whole table. {@code 0} disables the threshold
 * @param timezone                       timezone the timestamps inside the imported log files are expressed in; they
 *                                       are converted to the JVM's default timezone for storage, so that files written
 *                                       in different zones stay comparable. Passing the default timezone leaves the
 *                                       values unchanged
 * @param updateMetadataOnly             whether parsed logs are used only to add metadata to messages already
 *                                       stored, never to insert new chat lines or log files. Formatting, player, and
 *                                       server or world are always filled in where a message has none, live
 *                                       messages included. Known parsed versions fill missing file versions.
 *                                       A value is never cleared
 * @param overwriteFormatting            when {@code updateMetadataOnly}, also replace existing formatting of
 *                                       messages imported from files when the log line has formatting
 * @param overwriteMinecraftUser         the same for the Minecraft player
 * @param overwriteMinecraftServer       the same for the server or world
 * @param overwriteMinecraftVersion      the same for the Minecraft version, when the log has a known version
 * @param overwriteLive                  whether the chosen overwrites also apply to messages captured live
 */
public record ImportOptions(
    boolean recursive,
    boolean nestedArchives,
    String pathMatcher,
    int parallelism,
    boolean skipAlreadyImported,
    boolean optimize,
    int optimizeIfImportedFilesExceed,
    ZoneId timezone,
    boolean updateMetadataOnly,
    boolean overwriteFormatting,
    boolean overwriteMinecraftUser,
    boolean overwriteMinecraftServer,
    boolean overwriteMinecraftVersion,
    boolean overwriteLive
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
     * were newly stored. User-initiated imports always optimize the whole store.
     */
    public static final int STARTUP_OPTIMIZE_AFTER_MORE_THAN = 15;

    /**
     * Compatibility constructor for callers using the original option set. Version overwrites remain opt-in.
     */
    public ImportOptions(boolean recursive, boolean nestedArchives, String pathMatcher, int parallelism,
                         boolean skipAlreadyImported, boolean optimize, int optimizeIfImportedFilesExceed,
                         ZoneId timezone, boolean updateMetadataOnly, boolean overwriteFormatting,
                         boolean overwriteMinecraftUser, boolean overwriteMinecraftServer, boolean overwriteLive) {
        this(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported, optimize,
            optimizeIfImportedFilesExceed, timezone, updateMetadataOnly, overwriteFormatting,
            overwriteMinecraftUser, overwriteMinecraftServer, false, overwriteLive);
    }

    public ImportOptions {
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be at least 1, was " + parallelism);
        if (optimizeIfImportedFilesExceed < 0) {
            throw new IllegalArgumentException(
                "optimizeIfImportedFilesExceed must be at least 0, was " + optimizeIfImportedFilesExceed);
        }
        Objects.requireNonNull(timezone, "timezone");
    }

    /**
     * Parser count used when the caller does not pick a thread count: half the machine's CPUs,
     * at least one, so import work leaves cores free for the game.
     */
    public static int defaultParallelism() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }

    /**
     * Defaults to a recursive import of nested archives, half as many parsers as CPUs, replacing already
     * imported files, clustering and compacting afterwards, and treating log timestamps as local time.
     */
    public static ImportOptions defaults() {
        return new ImportOptions(true, true, null, defaultParallelism(), false, true, 0,
            ZoneId.systemDefault(), false, false, false, false, false);
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

    /**
     * Whether this run should parse logs that would normally be skipped because their bytes or live-session
     * marker already exist in the database. True when metadata is being patched and skip-already-imported is off.
     */
    public boolean reparseExistingLogs() {
        return updateMetadataOnly && !skipAlreadyImported;
    }

    /**
     * Whether any overwrite is chosen. Overwriting live messages only matters together with one.
     */
    public boolean overwritesAnyMetadata() {
        return overwriteFormatting || overwriteMinecraftUser || overwriteMinecraftServer || overwriteMinecraftVersion;
    }

    public ImportOptions withRecursive(boolean recursive) {
        return with(draft -> draft.recursive = recursive);
    }

    public ImportOptions withNestedArchives(boolean nestedArchives) {
        return with(draft -> draft.nestedArchives = nestedArchives);
    }

    /**
     * @param pathMatcher a glob such as {@code **&#47;logs&#47;**}, or {@code null} to accept every file
     */
    public ImportOptions withPathMatcher(String pathMatcher) {
        return with(draft -> draft.pathMatcher = pathMatcher);
    }

    public ImportOptions withParallelism(int parallelism) {
        return with(draft -> draft.parallelism = parallelism);
    }

    public ImportOptions withSkipAlreadyImported(boolean skipAlreadyImported) {
        return with(draft -> draft.skipAlreadyImported = skipAlreadyImported);
    }

    public ImportOptions withOptimize(boolean optimize) {
        return with(draft -> draft.optimize = optimize);
    }

    public ImportOptions withOptimizeIfImportedFilesExceed(int optimizeIfImportedFilesExceed) {
        return with(draft -> draft.optimizeIfImportedFilesExceed = optimizeIfImportedFilesExceed);
    }

    /**
     * Timezone the timestamps inside the imported logs are expressed in. Stored timestamps are converted from this
     * timezone to the JVM default timezone. Passing the default timezone, which is also what {@link #defaults()} uses,
     * leaves the values unchanged.
     */
    public ImportOptions withTimezone(ZoneId timezone) {
        return with(draft -> draft.timezone = timezone);
    }

    /**
     * @param timezone an IANA timezone id such as {@code America/New_York}, or {@code UTC}
     */
    public ImportOptions withTimezone(String timezone) {
        return withTimezone(ZoneId.of(timezone));
    }

    public ImportOptions withUpdateMetadataOnly(boolean updateMetadataOnly) {
        return with(draft -> draft.updateMetadataOnly = updateMetadataOnly);
    }

    public ImportOptions withOverwriteFormatting(boolean overwriteFormatting) {
        return with(draft -> draft.overwriteFormatting = overwriteFormatting);
    }

    public ImportOptions withOverwriteMinecraftUser(boolean overwriteMinecraftUser) {
        return with(draft -> draft.overwriteMinecraftUser = overwriteMinecraftUser);
    }

    public ImportOptions withOverwriteMinecraftServer(boolean overwriteMinecraftServer) {
        return with(draft -> draft.overwriteMinecraftServer = overwriteMinecraftServer);
    }

    public ImportOptions withOverwriteMinecraftVersion(boolean overwriteMinecraftVersion) {
        return with(draft -> draft.overwriteMinecraftVersion = overwriteMinecraftVersion);
    }

    public ImportOptions withOverwriteLive(boolean overwriteLive) {
        return with(draft -> draft.overwriteLive = overwriteLive);
    }

    /**
     * Whether this run should cluster and compact after {@code importedFiles} were stored or updated.
     */
    public boolean shouldOptimize(int importedFiles) {
        if (importedFiles <= 0) return false;
        if (optimize) return true;
        return optimizeIfImportedFilesExceed > 0 && importedFiles > optimizeIfImportedFilesExceed;
    }

    private ImportOptions with(Consumer<Draft> change) {
        Draft draft = new Draft(this);
        change.accept(draft);
        return draft.build();
    }

    /**
     * Mutable copy of the options, used only between a {@code with*} call and the record it builds.
     */
    private static final class Draft {
        private boolean recursive;
        private boolean nestedArchives;
        private String pathMatcher;
        private int parallelism;
        private boolean skipAlreadyImported;
        private boolean optimize;
        private int optimizeIfImportedFilesExceed;
        private ZoneId timezone;
        private boolean updateMetadataOnly;
        private boolean overwriteFormatting;
        private boolean overwriteMinecraftUser;
        private boolean overwriteMinecraftServer;
        private boolean overwriteMinecraftVersion;
        private boolean overwriteLive;

        private Draft(ImportOptions options) {
            recursive = options.recursive;
            nestedArchives = options.nestedArchives;
            pathMatcher = options.pathMatcher;
            parallelism = options.parallelism;
            skipAlreadyImported = options.skipAlreadyImported;
            optimize = options.optimize;
            optimizeIfImportedFilesExceed = options.optimizeIfImportedFilesExceed;
            timezone = options.timezone;
            updateMetadataOnly = options.updateMetadataOnly;
            overwriteFormatting = options.overwriteFormatting;
            overwriteMinecraftUser = options.overwriteMinecraftUser;
            overwriteMinecraftServer = options.overwriteMinecraftServer;
            overwriteMinecraftVersion = options.overwriteMinecraftVersion;
            overwriteLive = options.overwriteLive;
        }

        private ImportOptions build() {
            return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported,
                optimize, optimizeIfImportedFilesExceed, timezone, updateMetadataOnly, overwriteFormatting,
                overwriteMinecraftUser, overwriteMinecraftServer, overwriteMinecraftVersion, overwriteLive);
        }
    }
}
