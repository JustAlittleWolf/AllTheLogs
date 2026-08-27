package me.wolfii.allthelogs.client.timeline;

import me.wolfii.allthelogs.data.MatchDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineScaleTest {
    @Test
    void linearProgressKeepsTheEdgesInEitherBoundOrder() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime last = LocalDateTime.of(2026, 1, 2, 0, 0);
        assertEquals(0, TimelineScale.linearProgress(first, first, last));
        assertEquals(1, TimelineScale.linearProgress(last, first, last));
        assertEquals(0.5, TimelineScale.linearProgress(first.plusHours(12), first, last), 0.0001);
        assertEquals(0, TimelineScale.linearProgress(first, last, first));
        assertEquals(1, TimelineScale.linearProgress(last, last, first));
        assertEquals(first, TimelineScale.timeAtLinearProgress(0, first, last));
        assertEquals(last, TimelineScale.timeAtLinearProgress(1, first, last));
        assertEquals(0, TimelineScale.yAtProgress(0, 0, 100));
        assertEquals(99, TimelineScale.yAtProgress(1, 0, 100));
    }

    @Test
    void occupiedDaysSkipTheEmptyGapBetweenHits() {
        LocalDateTime jan2025 = LocalDateTime.of(2025, 1, 15, 12, 0);
        LocalDateTime jan2026 = LocalDateTime.of(2026, 1, 15, 12, 0);
        List<LocalDate> days = List.of(LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15));
        assertEquals(0.25, TimelineScale.dayProgress(jan2025, days), 0.02);
        assertEquals(0.75, TimelineScale.dayProgress(jan2026, days), 0.02);
        int y2025 = TimelineScale.yAtProgress(TimelineScale.dayProgress(jan2025, days), 0, 100);
        int y2026 = TimelineScale.yAtProgress(TimelineScale.dayProgress(jan2026, days), 0, 100);
        assertTrue(y2025 < 50);
        assertTrue(y2026 > 50);
        LocalDateTime linearMid = TimelineScale.timeAtLinearProgress(0.5, jan2025, jan2026);
        assertEquals(2025, linearMid.getYear());
        LocalDateTime occupiedMid = TimelineScale.timeAtProgress(0.5, matchDays(days));
        assertTrue(occupiedMid.getYear() == 2025 || occupiedMid.getYear() == 2026);
        assertEquals(1, occupiedMid.getMonthValue());
    }

    @Test
    void occupiedDaysDoNotFillEmptyDaysInsideAMonth() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime last = LocalDateTime.of(2026, 1, 31, 12, 0);
        List<LocalDate> days = List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertEquals(0.25, TimelineScale.dayProgress(first, days), 0.02);
        assertEquals(0.75, TimelineScale.dayProgress(last, days), 0.02);
        LocalDateTime mid = TimelineScale.timeAtProgress(0.5, matchDays(days));
        assertTrue(mid.getDayOfMonth() == 1 || mid.getDayOfMonth() == 31);
        assertEquals(0, TimelineScale.yAtProgress(
            TimelineScale.dayProgress(first.toLocalDate().atStartOfDay(), days), 0, 100));
        assertTrue(TimelineScale.yAtProgress(TimelineScale.dayProgress(last, days), 0, 100) > 50);
    }

    @Test
    void matchDaysUseRealFirstAndLastTimesInsteadOfTheWholeClock() {
        LocalDateTime fiveAm = LocalDateTime.of(2026, 1, 1, 5, 0);
        LocalDateTime sixAm = LocalDateTime.of(2026, 1, 1, 6, 0);
        LocalDateTime nextDay = LocalDateTime.of(2026, 1, 2, 5, 0);
        List<MatchDay> days = List.of(
            new MatchDay(fiveAm.toLocalDate(), fiveAm, sixAm, 10),
            new MatchDay(nextDay.toLocalDate(), nextDay, nextDay, 4));
        assertEquals(0, TimelineScale.matchDayProgress(fiveAm, days, 0), 0.0001);
        assertEquals(0.5 / 2, TimelineScale.matchDayProgress(fiveAm.plusMinutes(30), days, 0), 0.0001);
        assertEquals(-1, TimelineScale.skipAtProgress(0.1, days));
        assertEquals(-1, TimelineScale.skipAtProgress(0.1, days.subList(0, 1)));
        assertEquals(10, TimelineScale.skipAtProgress(0.5, days));
        assertEquals(13, TimelineScale.skipAtProgress(1, days));
        assertEquals(fiveAm, TimelineScale.timeAtProgress(0, days));
        assertEquals(nextDay, TimelineScale.timeAtProgress(1, days));
        assertEquals(days.get(1), TimelineScale.dayAtProgress(0.75, days));
    }

    private static List<MatchDay> matchDays(List<LocalDate> dates) {
        return dates.stream()
            .map(date -> new MatchDay(date, date.atStartOfDay(), date.atStartOfDay().plusHours(12), 1))
            .toList();
    }
}
