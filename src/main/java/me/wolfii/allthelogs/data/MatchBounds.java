package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Oldest and newest timestamps among matches for a {@link ChatQuery}, how many distinct calendar dates
 * those matches fall on, and the days that actually contain matches. The browser timeline maps only
 * those days so empty gaps between them are not on the scrubber.
 *
 * @param oldest      earliest match timestamp, or {@code null} when there are no matches
 * @param newest      latest match timestamp, or {@code null} when there are no matches
 * @param uniqueDates number of distinct dates among matches
 * @param dates       distinct calendar days that contain matches, oldest first
 */
public record MatchBounds(LocalDateTime oldest, LocalDateTime newest, int uniqueDates, List<LocalDate> dates) {
    public MatchBounds {
        dates = dates == null ? List.of() : List.copyOf(dates);
    }

    public static MatchBounds empty() {
        return new MatchBounds(null, null, 0, List.of());
    }

    /**
     * Distinct year-months that contain at least one matched day, oldest first.
     */
    public List<YearMonth> months() {
        if (dates.isEmpty()) return List.of();
        List<YearMonth> months = new ArrayList<>();
        YearMonth previous = null;
        for (LocalDate date : dates) {
            YearMonth month = YearMonth.from(date);
            if (!month.equals(previous)) {
                months.add(month);
                previous = month;
            }
        }
        return List.copyOf(months);
    }
}
