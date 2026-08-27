package me.wolfii.allthelogs.client.timeline;

import java.time.LocalDateTime;

/**
 * Where a scrubber drag wants the list to land.
 *
 * @param time     target timestamp, or {@code null} when only {@code skip} can address it
 * @param skip     match rank to skip to for a day collapsed onto one timestamp, or {@code -1} to jump by time
 * @param progress 0–1 position along the track, used to restore the scroll offset once the page arrives
 */
public record ScrubJump(LocalDateTime time, long skip, double progress) {
}
