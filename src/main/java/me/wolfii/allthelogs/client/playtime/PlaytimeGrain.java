package me.wolfii.allthelogs.client.playtime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Width of one bar on the timeline. {@link #AUTO} follows the visible range: days up to about two months,
 * weeks up to about two years, and months after that.
 */
public enum PlaytimeGrain {
    AUTO,
    DAYS,
    WEEKS,
    MONTHS;

    public static PlaytimeGrain resolve(PlaytimeGrain selected, LocalDateTime start, LocalDateTime end) {
        if (selected != null && selected != AUTO) return selected;
        long days = 0;
        if (start != null && end != null && end.isAfter(start)) {
            days = Duration.between(start, end).toDays();
        }
        if (days <= 62) return DAYS;
        if (days <= 730) return WEEKS;
        return MONTHS;
    }
}
