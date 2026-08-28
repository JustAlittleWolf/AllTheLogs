package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One occupied calendar day in a {@link MatchSummary}: the actual first and last match times
 * that day, and how many matches it holds. The timeline uses this so a day is not stretched across
 * empty hours, and a cluster at one timestamp can still be addressed by match rank.
 */
public record MatchDay(LocalDate date, LocalDateTime oldest, LocalDateTime newest, long matches)
    implements me.wolfii.allthelogs.api.MatchDay {
    public MatchDay {
        if (date == null) throw new IllegalArgumentException("date");
        if (matches < 0) throw new IllegalArgumentException("matches must not be negative");
    }
}
