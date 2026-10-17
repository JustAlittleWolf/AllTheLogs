package me.wolfii.allthelogs.client.playtime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Width of one bar on the timeline. {@link #AUTO} picks the coarsest interval whose bars stay at most
 * {@link #MAX_AUTO_BAR_WIDTH} wide on the chart, so zooming in switches to finer bars before they get wide.
 * Without a known chart width it falls back to days up to about six weeks, weeks up to a year, months up
 * to ten years, and years after that.
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
    /** Widest bar, in GUI pixels, that {@link #AUTO} allows before choosing a finer interval. */
    public static final int MAX_AUTO_BAR_WIDTH = 14;
    /** Zooming in stops once this many bars of the interval are visible; {@link #AUTO} counts days. */
    public static final int MIN_VISIBLE_BARS = 20;
    private static final PlaytimeGrain[] COARSE_TO_FINE = {YEARS, MONTHS, WEEKS};

    public static PlaytimeGrain resolve(PlaytimeGrain selected, LocalDateTime start, LocalDateTime end) {
        return resolve(selected, start, end, 0);
    }

    /**
     * @param chartWidth width of the plot in GUI pixels, or 0 when unknown
     */
    public static PlaytimeGrain resolve(PlaytimeGrain selected, LocalDateTime start, LocalDateTime end, int chartWidth) {
        if (selected != null && selected != AUTO) return selected;
        if (chartWidth > 0 && start != null && end != null && end.isAfter(start)) {
            double days = Duration.between(start, end).toSeconds() / 86_400d;
            for (PlaytimeGrain grain : COARSE_TO_FINE) {
                double bars = Math.max(1, days / grain.days());
                if (chartWidth / bars <= MAX_AUTO_BAR_WIDTH) return grain;
            }
            return DAYS;
        }
        long days = 0;
        if (start != null && end != null && end.isAfter(start)) {
            days = Duration.between(start, end).toDays();
        }
        if (days <= MAX_DAYS) return DAYS;
        if (days <= MAX_WEEKS) return WEEKS;
        if (days <= MAX_MONTHS) return MONTHS;
        return YEARS;
    }

    /**
     * Narrowest view for this interval: {@link #MIN_VISIBLE_BARS} bars, or days for {@link #AUTO}.
     */
    public long minViewSeconds() {
        return Math.round(MIN_VISIBLE_BARS * days() * 86_400);
    }

    private double days() {
        return switch (this) {
            case WEEKS -> 7;
            case MONTHS -> 30.44;
            case YEARS -> 365.25;
            case DAYS, AUTO -> 1;
        };
    }
}
