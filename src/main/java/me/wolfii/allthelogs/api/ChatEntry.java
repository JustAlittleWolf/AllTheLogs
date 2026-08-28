package me.wolfii.allthelogs.api;

import java.time.LocalDateTime;

/**
 * A single chat line from a Minecraft log, stored with legacy {@code §} codes stripped.
 */
public interface ChatEntry {
    /**
     * The imported log or client session this line came from.
     */
    ChatLog chatLog();

    /**
     * The date of the log combined with the time of the log line, converted from the import timezone to
     * the JVM default timezone.
     */
    LocalDateTime timestamp();

    /**
     * Zero-based position of this entry among the chat entries of its log. Consecutive entries of one
     * log have consecutive indices, which is what makes retrieving surrounding lines cheap.
     */
    int lineIndex();

    /**
     * The chat text with legacy {@code §} codes stripped.
     */
    String message();

    /**
     * Packed formatting runs into {@link #message()} ({@code long} per range), or {@code null}.
     */
    long[] formatting();
}
