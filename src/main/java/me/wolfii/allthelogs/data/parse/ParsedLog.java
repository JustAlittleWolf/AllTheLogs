package me.wolfii.allthelogs.data.parse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * The result of parsing one log file.
 *
 * @param minecraftVersion         the detected version, or {@link me.wolfii.allthelogs.data.ChatLog#UNKNOWN_VERSION}
 * @param minecraftUser            the player from a {@code Setting user:} line (or first LAN login), or {@code null}
 * @param serverOrWorld            last remote address or {@code world/{name}} seen in the file, or {@code null}
 * @param entries                  chat lines in the order they appeared
 * @param resourceManagerReloaded  whether the file contains a {@code Reloading ResourceManager} line, which marks it
 *                                 as a log worth keeping even when it has no chat entries
 * @param firstLineDate            calendar date from the first timestamped line when that line included one,
 *                                 {@code null} for time-only logs
 * @param firstLineTime            wall clock time of the first line with a recognisable timestamp, {@code null} if none
 * @param lastLineDate             calendar date from the last timestamped line when that line included one,
 *                                 {@code null} for time-only logs
 * @param lastLineTime             wall clock time of the last line with a recognisable timestamp, {@code null} if none
 * @param sessionId                id from an {@link me.wolfii.allthelogs.data.store.SessionMarker} line, or {@code null}
 */
public record ParsedLog(
    String minecraftVersion,
    String minecraftUser,
    String serverOrWorld,
    List<Entry> entries,
    boolean resourceManagerReloaded,
    LocalDate firstLineDate,
    LocalTime firstLineTime,
    LocalDate lastLineDate,
    LocalTime lastLineTime,
    String sessionId
) {
    /**
     * @param date           calendar date from the line prefix, or {@code null} when the line is time-only
     * @param time           the wall clock time of the log line
     * @param message        everything after {@code [CHAT] }, without legacy {@code §} codes
     * @param formatting     packed {@code (offset, count, format)} triples, or {@code null}
     * @param minecraftUser  player in effect at this line, or {@code null}
     * @param serverOrWorld  remote address or {@code world/{name}} in effect at this line, or {@code null}
     */
    public record Entry(LocalDate date, LocalTime time, String message, long[] formatting, String minecraftUser,
                        String serverOrWorld) {
        public Entry(LocalTime time, String message) {
            this(null, time, message, null, null, null);
        }

        public Entry(LocalTime time, String message, long[] formatting) {
            this(null, time, message, formatting, null, null);
        }
    }
}
