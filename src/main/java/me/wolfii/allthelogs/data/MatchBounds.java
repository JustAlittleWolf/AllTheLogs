package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;

/**
 * Oldest and newest timestamps among matches for a {@link ChatQuery}, plus how many distinct calendar dates
 * those matches fall on. Used by the browser timeline so it can span only matched logs.
 *
 * @param oldest      earliest match timestamp, or {@code null} when there are no matches
 * @param newest      latest match timestamp, or {@code null} when there are no matches
 * @param uniqueDates number of distinct dates among matches
 */
public record MatchBounds(LocalDateTime oldest, LocalDateTime newest, int uniqueDates) {
    public static MatchBounds empty() {
        return new MatchBounds(null, null, 0);
    }
}
