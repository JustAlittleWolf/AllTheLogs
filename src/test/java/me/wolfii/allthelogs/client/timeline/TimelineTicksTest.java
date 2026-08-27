package me.wolfii.allthelogs.client.timeline;

import me.wolfii.allthelogs.data.MatchDay;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTicksTest {
    @Test
    void hoverLabelDropsTheClockOnceThereAreManyDates() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertEquals("Jan 1, 2026 00:00", TimelineTicks.hoverLabel(first, 2));
        assertEquals("Jan 1, 2026", TimelineTicks.hoverLabel(first, 5));
    }

    @Test
    void occupiedDaysGetOneTickEachWhileThereAreFewOfThem() {
        List<LocalDate> days = List.of(LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15));
        List<TimelineTicks.DateTick> ticks = TimelineTicks.occupiedDayTicks(days);
        assertEquals(2, ticks.size());
        assertEquals(LocalDate.of(2025, 1, 15), ticks.getFirst().at().toLocalDate());
    }

    @Test
    void spacedTicksKeepAReadableGapOnAnOldestFirstTrack() {
        LocalDateTime oldest = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime newest = LocalDateTime.of(2026, 8, 1, 0, 0);
        List<TimelineTicks.DateTick> ticks = TimelineTicks.spacedTicks(oldest, newest, List.of(), 80, 16);
        assertTrue(ticks.size() >= 2);
        int previousY = Integer.MIN_VALUE / 2;
        for (TimelineTicks.DateTick tick : ticks) {
            int y = TimelineScale.yAtProgress(TimelineScale.linearProgress(tick.at(), oldest, newest), 0, 80);
            assertTrue(y - previousY >= 16, () -> "ticks overlap at " + tick.label());
            previousY = y;
        }
    }

    @Test
    void spacedOccupiedDaysUseEqualSharePlacement() {
        LocalDateTime oldest = LocalDateTime.of(2025, 1, 15, 0, 0);
        LocalDateTime newest = LocalDateTime.of(2026, 1, 15, 0, 0);
        List<MatchDay> days = List.of(
            new MatchDay(oldest.toLocalDate(), oldest, oldest.plusHours(1), 1),
            new MatchDay(newest.toLocalDate(), newest, newest.plusHours(1), 1));
        List<TimelineTicks.DateTick> ticks = TimelineTicks.spacedTicks(oldest, newest, days, 200, 16);
        assertEquals(2, ticks.size());
    }
}
