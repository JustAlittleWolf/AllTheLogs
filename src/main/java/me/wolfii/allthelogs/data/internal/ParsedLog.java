package me.wolfii.allthelogs.data.internal;

import java.time.LocalTime;
import java.util.List;

/// The result of parsing one log file: its detected Minecraft version, its chat lines in file order, and the time
/// bounds of every logged line, not just chat lines.
///
/// @param minecraftVersion       the detected version, or [me.wolfii.allthelogs.data.ChatLog#UNKNOWN_VERSION]
/// @param entries                chat lines in the order they appeared
/// @param resourceManagerReloaded whether the file contains a `Reloading ResourceManager` line, which marks it as a
///                               log worth keeping even when it has no chat entries
/// @param firstLineTime          wall clock time of the first line with a recognisable timestamp, `null` if none
/// @param lastLineTime           wall clock time of the last line with a recognisable timestamp, `null` if none
public record ParsedLog(
    String minecraftVersion,
    List<Entry> entries,
    boolean resourceManagerReloaded,
    LocalTime firstLineTime,
    LocalTime lastLineTime
) {
    /// @param time    the wall clock time of the log line
    /// @param message everything after `[CHAT] `, unmodified
    public record Entry(LocalTime time, String message) {
    }
}
