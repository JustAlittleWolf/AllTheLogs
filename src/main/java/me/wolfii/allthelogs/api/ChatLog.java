package me.wolfii.allthelogs.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A group of chat entries from one imported log or one client session.
 */
public interface ChatLog {
    /**
     * Where this log was read from.
     */
    LogSource source();

    /**
     * The calendar date the log belongs to.
     */
    LocalDate date();

    /**
     * The Minecraft version the log was produced by, or {@code null} if the log does not name one.
     */
    String minecraftVersion();

    /**
     * Timestamp of the first logged line, not just chat entries; converted from the import timezone like chat
     * entries. For a client session this is when the session started.
     */
    LocalDateTime startTime();

    /**
     * Timestamp of the last logged line, not just chat entries; converted from the import timezone like chat
     * entries. For a client session this is updated as live chat arrives.
     */
    LocalDateTime endTime();

    /**
     * The player from a client identity marker, or {@code null} if unknown. Per-line values live on
     * {@link ChatEntry#minecraftUser()}.
     */
    String minecraftUser();
}
