package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Unpaged match metadata for a {@link ChatQuery}: how many hits there are, the first and last timestamps,
 * and one {@link MatchDay} per occupied calendar day. Built from a single {@code GROUP BY} date aggregation,
 * which is much cheaper than loading every matching row (no message text or formatting).
 *
 * @param oldest  earliest match timestamp, or {@code null} when there are no matches
 * @param newest  latest match timestamp, or {@code null} when there are no matches
 * @param matches total matching entries; the sum of {@link MatchDay#matches()}
 * @param days    occupied days, oldest first
 */
public record MatchSummary(LocalDateTime oldest, LocalDateTime newest, long matches, List<MatchDay> days) {
    public MatchSummary {
        days = days == null ? List.of() : List.copyOf(days);
        if (matches < 0) throw new IllegalArgumentException("matches must not be negative");
    }

    public static MatchSummary empty() {
        return new MatchSummary(null, null, 0, List.of());
    }

    /**
     * Derives oldest, newest, and total count from per-day rows of one aggregation query.
     */
    public static MatchSummary of(List<MatchDay> days) {
        if (days == null || days.isEmpty()) return empty();
        List<MatchDay> copy = List.copyOf(days);
        LocalDateTime oldest = copy.getFirst().oldest();
        LocalDateTime newest = copy.getLast().newest();
        long total = 0;
        for (MatchDay day : copy) {
            if (oldest == null || (day.oldest() != null && day.oldest().isBefore(oldest))) {
                oldest = day.oldest();
            }
            if (newest == null || (day.newest() != null && day.newest().isAfter(newest))) {
                newest = day.newest();
            }
            total += day.matches();
        }
        return new MatchSummary(oldest, newest, total, copy);
    }

    public int uniqueDates() {
        return days.size();
    }

    public List<LocalDate> dates() {
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
        if (days.isEmpty()) return List.of();
        List<YearMonth> months = new ArrayList<>();
        YearMonth previous = null;
        for (MatchDay day : days) {
            YearMonth month = YearMonth.from(day.date());
            if (!month.equals(previous)) {
                months.add(month);
                previous = month;
            }
        }
        return List.copyOf(months);
    }
}
