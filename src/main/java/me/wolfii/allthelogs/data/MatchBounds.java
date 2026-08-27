package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Oldest and newest timestamps among matches for a {@link ChatQuery}, how many distinct calendar dates
 * those matches fall on, and the year-months that actually contain matches. The browser timeline maps
 * only those months so empty gaps between them are not on the scrubber.
 *
 * @param oldest      earliest match timestamp, or {@code null} when there are no matches
 * @param newest      latest match timestamp, or {@code null} when there are no matches
 * @param uniqueDates number of distinct dates among matches
 * @param months      distinct year-months that contain matches, oldest first
 */
public record MatchBounds(LocalDateTime oldest, LocalDateTime newest, int uniqueDates, List<YearMonth> months) {
    public MatchBounds {
        months = months == null ? List.of() : List.copyOf(months);
    }

    public static MatchBounds empty() {
        return new MatchBounds(null, null, 0, List.of());
    }
}
