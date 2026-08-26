package me.wolfii.allthelogs.data.internal;

import java.time.Instant;

/// A log file that was discovered and is ready to be parsed, with its contents already materialised in memory.
///
/// Archive entries cannot be read lazily from several threads, so discovery reads the raw bytes up front and hands
/// them to the parsing stage. Log files are small enough that this is cheaper than coordinating access to the archive.
///
/// @param fileName     bare file name
/// @param sourceKind   whether it came from a file on disk or an archive
/// @param sourcePath   absolute path of the log file, or of the import root for an archive
/// @param entryPath    path within the archive, always `/` separated, nested archives separated by `!/`; empty for a
///                     file on disk
/// @param lastModified last modification instant, or `null` if the source does not report one
/// @param content      raw file bytes, still gzip compressed if the file name ends in `.gz`
public record LogCandidate(
    String fileName,
    SourceKind sourceKind,
    String sourcePath,
    String entryPath,
    Instant lastModified,
    byte[] content
) {
}
