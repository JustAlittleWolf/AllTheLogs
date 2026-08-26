package me.wolfii.allthelogs.data;

import java.util.List;

/// Summary of a completed import run.
///
/// @param importedFiles number of log files that were parsed and stored
/// @param skippedFiles  number of candidate files that were skipped, either because they were already imported or
///                      because they contained no chat entries
/// @param importedEntries total number of chat entries stored, after entries duplicating an already stored timestamp
///                      and message were dropped
/// @param failures      files that could not be read, with the reason
public record ImportResult(
    int importedFiles,
    int skippedFiles,
    long importedEntries,
    List<Failure> failures
) {
    /// @param path   the path of the file that failed, formatted like [LogFile#entryPath()]
    /// @param reason human readable description of what went wrong
    public record Failure(String path, String reason) {
    }
}
