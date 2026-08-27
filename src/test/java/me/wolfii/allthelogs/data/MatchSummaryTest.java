package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatchSummaryTest {
    @Test
    void ofSumsDayCountsAndTakesExtremeTimes() {
        MatchDay first = new MatchDay(LocalDate.of(2026, 1, 1),
            LocalDateTime.of(2026, 1, 1, 5, 0),
            LocalDateTime.of(2026, 1, 1, 6, 0),
            2);
        MatchDay last = new MatchDay(LocalDate.of(2026, 1, 3),
            LocalDateTime.of(2026, 1, 3, 8, 0),
            LocalDateTime.of(2026, 1, 3, 9, 0),
            3);
        MatchSummary summary = MatchSummary.of(List.of(first, last));
        assertEquals(5, summary.matches());
        assertEquals(first.oldest(), summary.oldest());
        assertEquals(last.newest(), summary.newest());
        assertEquals(2, summary.uniqueDates());
    }

    @Test
    void ofEmptyIsEmpty() {
        MatchSummary summary = MatchSummary.of(List.of());
        assertEquals(0, summary.matches());
        assertNull(summary.oldest());
        assertNull(summary.newest());
    }
}
