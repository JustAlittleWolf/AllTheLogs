package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;

/**
 * A single chat line from a Minecraft log, stored raw (everything after {@code [CHAT] }).
 *
 * @param chatLog   the imported log or client session this line came from
 * @param timestamp the date of the log combined with the time of the log line, converted from the import timezone to
 *                  the JVM default timezone
 * @param lineIndex zero based position of this entry among the chat entries of its log; consecutive entries of one
 *                  log have consecutive indices, which is what makes retrieving surrounding lines cheap
 * @param message   the raw chat text, including any legacy formatting codes
 */
public record ChatEntry(
    ChatLog chatLog,
    LocalDateTime timestamp,
    int lineIndex,
    String message
) {
}
