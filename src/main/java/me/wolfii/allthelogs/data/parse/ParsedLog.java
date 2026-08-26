package me.wolfii.allthelogs.data.parse;

import java.time.LocalTime;
import java.util.List;

/**
 * The result of parsing one log file.
 *
 * @param minecraftVersion         the detected version, or {@link me.wolfii.allthelogs.data.ChatLog#UNKNOWN_VERSION}
 * @param entries                  chat lines in the order they appeared
 * @param resourceManagerReloaded  whether the file contains a {@code Reloading ResourceManager} line, which marks it
 *                                 as a log worth keeping even when it has no chat entries
 * @param firstLineTime            wall clock time of the first line with a recognisable timestamp, {@code null} if none
 * @param lastLineTime             wall clock time of the last line with a recognisable timestamp, {@code null} if none
 */
public record ParsedLog(
    String minecraftVersion,
    List<Entry> entries,
    boolean resourceManagerReloaded,
    LocalTime firstLineTime,
    LocalTime lastLineTime
) {
    /**
     * @param time    the wall clock time of the log line
     * @param message everything after {@code [CHAT] }, unmodified
     */
    public record Entry(LocalTime time, String message) {
    }
}
