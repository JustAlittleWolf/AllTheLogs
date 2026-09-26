package me.wolfii.allthelogs.api;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A live chat line waiting to be written into the open session.
 * <p>
 * Capture time, player, and server or world are fixed when the line is queued, so a later insert does not
 * move them. A null or blank {@code minecraftUser} keeps the last known player. A null or blank
 * {@code serverOrWorld} clears the place. {@code formatting} is packed runs into {@code text}; {@code null}
 * asks the store to parse legacy {@code §} codes from the text, and an empty array stores the text unchanged.
 */
public record PendingLiveMessage(
    String text,
    long[] formatting,
    String minecraftUser,
    String serverOrWorld,
    LocalDateTime capturedAt
) {
    public PendingLiveMessage {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(capturedAt, "capturedAt");
        formatting = formatting == null ? null : formatting.clone();
    }
}
