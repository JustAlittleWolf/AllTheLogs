package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.SourceKind;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/// A parsed log file, carrying everything the writer needs to store it.
///
/// @param entryTimes               timestamps of the chat entries, parallel to [#messages]
/// @param resourceManagerReloaded  whether the file contains a `Reloading ResourceManager` line; such files are kept
///                                 even when they have no chat entries
/// @param firstLineTime            timestamp of the first logged line of the file, not just chat entries
/// @param lastLineTime             timestamp of the last logged line of the file, not just chat entries
public record PreparedLog(
    String fileName,
    SourceKind sourceKind,
    String sourcePath,
    String entryPath,
    LocalDate date,
    String minecraftVersion,
    List<LocalDateTime> entryTimes,
    List<String> messages,
    boolean resourceManagerReloaded,
    LocalDateTime firstLineTime,
    LocalDateTime lastLineTime
) {
}
