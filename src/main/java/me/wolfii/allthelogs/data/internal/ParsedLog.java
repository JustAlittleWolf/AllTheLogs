package me.wolfii.allthelogs.data.internal;

import java.time.LocalTime;
import java.util.List;

/// The result of parsing one log file: its detected Minecraft version and its chat lines in file order.
///
/// @param minecraftVersion the detected version, or [me.wolfii.allthelogs.data.LogFile#UNKNOWN_VERSION]
/// @param entries          chat lines in the order they appeared
public record ParsedLog(String minecraftVersion, List<Entry> entries) {
    /// @param time    the wall clock time of the log line
    /// @param message everything after `[CHAT] `, unmodified
    public record Entry(LocalTime time, String message) {
    }
}
