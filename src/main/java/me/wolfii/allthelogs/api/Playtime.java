package me.wolfii.allthelogs.api;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One stretch of estimated playtime where the Minecraft version and the server or world stay the same.
 * {@code end} is exclusive. {@code serverOrWorld} is null when no chat line in the stretch names a place.
 */
public record Playtime(LocalDateTime start, LocalDateTime end, String minecraftVersion, String serverOrWorld) {
    public Playtime {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    /**
     * Length of this stretch in milliseconds.
     */
    public long millis() {
        return Duration.between(start, end).toMillis();
    }
}
