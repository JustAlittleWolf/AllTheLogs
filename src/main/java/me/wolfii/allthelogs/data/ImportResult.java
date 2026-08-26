package me.wolfii.allthelogs.data;

import java.util.List;

/// Summary of a completed import run.
///
/// @param importedFiles number of log files that were parsed and stored, including empty ones
/// @param skippedFiles  number of candidate files that were not stored, because they were already imported, had
///                      no usable timestamps, or had no chat content and no `Reloading ResourceManager` line
/// @param emptyFiles    number of stored log files that contained no chat lines
/// @param importedEntries total number of chat entries stored, after entries duplicating an already stored timestamp
///                      and message were dropped
/// @param failures      files that could not be read, with the reason
public record ImportResult(
    int importedFiles,
    int skippedFiles,
    int emptyFiles,
    long importedEntries,
    List<Failure> failures
) {
    /// @param path   the path of the file that failed; for archives this is [LogSource.Archive#entryPath()]
    /// @param reason human readable description of what went wrong
    public record Failure(String path, String reason) {
    }
}
