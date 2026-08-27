package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Oldest and newest timestamps among matches for a {@link ChatQuery}, how many distinct calendar dates
 * those matches fall on, and the days that actually contain matches. The browser timeline maps only
 * those days so empty gaps between them are not on the scrubber. Each day also carries its real
 * first/last match times and match count so progress follows the messages, not empty hours.
 *
 * @param oldest      earliest match timestamp, or {@code null} when there are no matches
 * @param newest      latest match timestamp, or {@code null} when there are no matches
 * @param uniqueDates number of distinct dates among matches
 * @param dates       distinct calendar days that contain matches, oldest first
 * @param days        occupied days with per-day bounds and counts, oldest first
 */
public record MatchBounds(LocalDateTime oldest, LocalDateTime newest, int uniqueDates, List<LocalDate> dates,
                          List<MatchDay> days) {
    public MatchBounds {
        days = days == null ? List.of() : List.copyOf(days);
        if (dates == null || dates.isEmpty()) {
            dates = datesOf(days);
        } else {
            dates = List.copyOf(dates);
        }
    }

    public MatchBounds(LocalDateTime oldest, LocalDateTime newest, int uniqueDates, List<LocalDate> dates) {
        this(oldest, newest, uniqueDates, dates, List.of());
    }

    public static MatchBounds empty() {
        return new MatchBounds(null, null, 0, List.of(), List.of());
    }

    private static List<LocalDate> datesOf(List<MatchDay> days) {
        if (days.isEmpty()) return List.of();
        List<LocalDate> dates = new ArrayList<>(days.size());
        for (MatchDay day : days) {
            dates.add(day.date());
        }
        return List.copyOf(dates);
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
