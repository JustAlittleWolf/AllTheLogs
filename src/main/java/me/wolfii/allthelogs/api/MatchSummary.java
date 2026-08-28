package me.wolfii.allthelogs.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Unpaged match metadata for a {@link ChatQuery}: how many hits there are, the first and last timestamps,
 * and one {@link MatchDay} per occupied calendar day.
 */
public interface MatchSummary {
    /**
     * Earliest match timestamp, or {@code null} when there are no matches.
     */
    LocalDateTime oldest();

    /**
     * Latest match timestamp, or {@code null} when there are no matches.
     */
    LocalDateTime newest();

    /**
     * Total matching entries; the sum of {@link MatchDay#matches()}.
     */
    long matches();

    /**
     * Occupied days, oldest first.
     */
    List<? extends MatchDay> days();

    default int uniqueDates() {
        return days().size();
    }

    default List<LocalDate> dates() {
        List<? extends MatchDay> days = days();
        if (days.isEmpty()) return List.of();
        List<LocalDate> dates = new ArrayList<>(days.size());
        for (MatchDay day : days) {
            dates.add(day.date());
        }
        return List.copyOf(dates);
    }
}
