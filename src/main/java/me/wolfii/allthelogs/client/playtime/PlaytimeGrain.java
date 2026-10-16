package me.wolfii.allthelogs.client.playtime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Width of one bar on the timeline. {@link #AUTO} follows the visible range: days up to about six weeks,
 * weeks up to a year, months up to ten years, and years after that.
 */
public enum PlaytimeGrain {
    AUTO,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS;

    static final long MAX_DAYS = 45;
    static final long MAX_WEEKS = 366;
    static final long MAX_MONTHS = 3_660;

    public static PlaytimeGrain resolve(PlaytimeGrain selected, LocalDateTime start, LocalDateTime end) {
        if (selected != null && selected != AUTO) return selected;
        long days = 0;
        if (start != null && end != null && end.isAfter(start)) {
            days = Duration.between(start, end).toDays();
        }
        if (days <= MAX_DAYS) return DAYS;
        if (days <= MAX_WEEKS) return WEEKS;
        if (days <= MAX_MONTHS) return MONTHS;
        return YEARS;
    }
}
