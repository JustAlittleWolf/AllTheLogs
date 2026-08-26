package me.wolfii.allthelogs.data;

/// Tuning knobs for an import run. Create one via [#defaults()] and derive variants with the `with*` methods.
///
/// @param recursive       for directory imports, whether to descend into subdirectories; for archive imports, whether
///                        to descend into directories of the archive
/// @param nestedArchives  whether archives found inside the imported tree (or inside the imported archive) are opened
///                        and imported as well
/// @param pathMatcher     glob restricting which log files are considered, matched against the path of the file
///                        relative to the import root, e.g. `**&#47;logs&#47;**`; `null` accepts everything
/// @param parallelism     number of log files parsed concurrently
/// @param skipAlreadyImported whether files whose source and entry path are already present in the database are
///                        skipped instead of replaced
public record ImportOptions(
    boolean recursive,
    boolean nestedArchives,
    String pathMatcher,
    int parallelism,
    boolean skipAlreadyImported
) {
    public ImportOptions {
        if (parallelism < 1) throw new IllegalArgumentException("parallelism must be at least 1, was " + parallelism);
    }

    public static ImportOptions defaults() {
        return new ImportOptions(true, true, null, Runtime.getRuntime().availableProcessors(), false);
    }

    public ImportOptions withRecursive(boolean recursive) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported);
    }

    public ImportOptions withNestedArchives(boolean nestedArchives) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported);
    }

    /// @param pathMatcher a glob such as `**&#47;logs&#47;**`, or `null` to accept every file
    public ImportOptions withPathMatcher(String pathMatcher) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported);
    }

    public ImportOptions withParallelism(int parallelism) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported);
    }

    public ImportOptions withSkipAlreadyImported(boolean skipAlreadyImported) {
        return new ImportOptions(recursive, nestedArchives, pathMatcher, parallelism, skipAlreadyImported);
    }
}
