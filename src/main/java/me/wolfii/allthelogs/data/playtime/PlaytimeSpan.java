package me.wolfii.allthelogs.data.playtime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One stretch of estimated playtime where the Minecraft version and the server or world stay the same.
 * {@code end} is exclusive.
 *
 * @param server the server or world in effect, or {@code null} when the log never names one for this stretch
 */
public record PlaytimeSpan(LocalDateTime start, LocalDateTime end, String version, String server) {
    public PlaytimeSpan {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(version, "version");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public long millis() {
        return Duration.between(start, end).toMillis();
    }
}
