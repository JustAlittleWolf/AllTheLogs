package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A group of chat entries from one imported log or one client session.
 *
 * @param source           where this log was read from
 * @param date             the calendar date the log belongs to
 * @param minecraftVersion the Minecraft version the log was produced by, or {@code unknown}
 * @param startTime        timestamp of the first logged line, not just chat entries; converted from the import
 *                         timezone like chat entries. For a client session this is when the session started
 * @param endTime          timestamp of the last logged line, not just chat entries; converted from the import
 *                         timezone like chat entries. For a client session this is updated by
 *                         {@link LogStore#importSessionMessage(String)}
 * @param minecraftUser    the player from a {@code Setting user:} line, or {@code null} if unknown
 */
public record ChatLog(
    LogSource source,
    LocalDate date,
    String minecraftVersion,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String minecraftUser
) {
    /**
     * Placeholder used when the Minecraft version could not be determined from the log contents.
     */
    public static final String UNKNOWN_VERSION = "unknown";

    public ChatLog {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
    }

    public ChatLog(LogSource source, LocalDate date, String minecraftVersion, LocalDateTime startTime,
                   LocalDateTime endTime) {
        this(source, date, minecraftVersion, startTime, endTime, null);
    }
}
