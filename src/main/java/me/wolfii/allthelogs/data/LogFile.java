package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/// Metadata about a single imported Minecraft log file.
///
/// @param fileName        the bare file name, e.g. `2026-08-25-2.log.gz`
/// @param sourceKind      whether the file came from a directory or an archive; empty for a client session
/// @param sourcePath      absolute path of the root that was imported (the directory or the outermost archive), or
///                        a sentinel for a client session
/// @param entryPath       path of the log inside that root; for directory imports this is the path relative to the
///                        imported directory, for archives the slash separated path inside the archive, with nested
///                        archives separated by `!/`
/// @param date            the calendar date the log belongs to
/// @param minecraftVersion the Minecraft version the log was produced by, or `unknown`
/// @param lastModified    last modification time as reported by the file system or archive, if available
/// @param firstEntryTime  timestamp of the first logged line of this file, not just chat entries; converted from the
///                        import timezone like chat entries. For a client session this is when the session started
/// @param lastEntryTime   timestamp of the last logged line of this file, not just chat entries; converted from the
///                        import timezone like chat entries. For a client session this is updated each time a message
///                        is imported with [LogStore#importSessionMessage(String)]
/// @param entryCount      number of stored chat entries of this file
public record LogFile(
    String fileName,
    Optional<SourceKind> sourceKind,
    String sourcePath,
    String entryPath,
    LocalDate date,
    String minecraftVersion,
    Optional<LocalDateTime> lastModified,
    Optional<LocalDateTime> firstEntryTime,
    Optional<LocalDateTime> lastEntryTime,
    long entryCount
) {
    /// Placeholder used when the Minecraft version could not be determined from the log contents.
    public static final String UNKNOWN_VERSION = "unknown";
}
