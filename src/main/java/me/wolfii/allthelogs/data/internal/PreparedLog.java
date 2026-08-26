package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.DateSource;
import me.wolfii.allthelogs.data.SourceKind;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/// A parsed log file, carrying everything the writer needs to store it.
///
/// @param entryTimes timestamps of the chat entries, parallel to [#messages]
public record PreparedLog(
    String fileName,
    SourceKind sourceKind,
    String sourcePath,
    String entryPath,
    LocalDate date,
    DateSource dateSource,
    String minecraftVersion,
    LocalDateTime lastModified,
    List<LocalDateTime> entryTimes,
    List<String> messages
) {
}
