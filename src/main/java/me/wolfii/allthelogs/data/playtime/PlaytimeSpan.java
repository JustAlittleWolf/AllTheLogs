package me.wolfii.allthelogs.data.playtime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One stretch of estimated playtime where the Minecraft version, the server or world, and the player stay the
 * same. {@code end} is exclusive.
 *
 * @param server the server or world in effect, or {@code null} when the log never names one for this stretch
 * @param player the Minecraft user playing, or {@code null} when the log does not name one
 */
public record PlaytimeSpan(LocalDateTime start, LocalDateTime end, String version, String server, String player) {
    public PlaytimeSpan {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(version, "version");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    /**
     * A span whose player is not known.
     */
    public PlaytimeSpan(LocalDateTime start, LocalDateTime end, String version, String server) {
        this(start, end, version, server, null);
    }

    public PlaytimeSpan withStartAndEnd(LocalDateTime start, LocalDateTime end) {
        return new PlaytimeSpan(start, end, version, server, player);
    }

    public long millis() {
        return Duration.between(start, end).toMillis();
    }
}
