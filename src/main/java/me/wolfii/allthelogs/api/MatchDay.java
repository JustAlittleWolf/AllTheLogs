package me.wolfii.allthelogs.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One occupied calendar day in a {@link MatchSummary}: the actual first and last match times
 * that day, and how many matches it holds.
 */
public interface MatchDay {
    LocalDate date();

    LocalDateTime oldest();

    LocalDateTime newest();

    long matches();

    /**
     * Whether every match on this day shares the same timestamp, so clock-time cannot distinguish them.
     */
    default boolean collapsed() {
        return oldest() == null || newest() == null || !newest().isAfter(oldest());
    }
}
