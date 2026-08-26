package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/// A group of chat entries from one imported log or one client session.
///
/// @param source           where this log was read from
/// @param date             the calendar date the log belongs to
/// @param minecraftVersion the Minecraft version the log was produced by, or `unknown`
/// @param firstEntryTime   timestamp of the first logged line, not just chat entries; converted from the import
///                         timezone like chat entries. For a client session this is when the session started
/// @param lastEntryTime    timestamp of the last logged line, not just chat entries; converted from the import
///                         timezone like chat entries. For a client session this is updated each time a message is
///                         imported with [LogStore#importSessionMessage(String)]
/// @param entryCount       number of stored chat entries
public record ChatLog(
    LogSource source,
    LocalDate date,
    String minecraftVersion,
    LocalDateTime firstEntryTime,
    LocalDateTime lastEntryTime,
    long entryCount
) {
    /// Placeholder used when the Minecraft version could not be determined from the log contents.
    public static final String UNKNOWN_VERSION = "unknown";

    public ChatLog {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(firstEntryTime, "firstEntryTime");
        Objects.requireNonNull(lastEntryTime, "lastEntryTime");
    }
}
