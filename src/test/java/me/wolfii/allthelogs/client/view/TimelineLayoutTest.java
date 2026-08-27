package me.wolfii.allthelogs.client.view;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineLayoutTest {
    @Test
    void timelineProgressAndDownsampleKeepTheEdges() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime last = LocalDateTime.of(2026, 1, 2, 0, 0);
        assertEquals(0, TimelineLayout.progress(first, first, last));
        assertEquals(1, TimelineLayout.progress(last, first, last));
        assertEquals(0.5, TimelineLayout.progress(first.plusHours(12), first, last), 0.0001);

        LocalDateTime newestFirstStart = last;
        LocalDateTime newestFirstEnd = first;
        assertEquals(0, TimelineLayout.progress(first, newestFirstStart, newestFirstEnd));
        assertEquals(1, TimelineLayout.progress(last, newestFirstStart, newestFirstEnd));
        assertEquals(0, TimelineLayout.yFromNewest(last, first, last, 0, 100));
        assertEquals(99, TimelineLayout.yFromNewest(first, first, last, 0, 100));
        assertEquals(last, TimelineLayout.timeFromNewest(0, first, last));
        assertEquals(first, TimelineLayout.timeFromNewest(1, first, last));
        assertEquals(first, TimelineLayout.oldest(List.of(last, first.plusHours(3), first)));
        assertEquals(last, TimelineLayout.newest(List.of(first, last, first.plusHours(3))));

        List<LocalDateTime> times = List.of(first, first.plusHours(1), first.plusHours(2), last);
        List<LocalDateTime> sampled = TimelineLayout.downsample(times, 10, 20);
        assertEquals(first, sampled.getFirst());
        assertEquals(last, sampled.getLast());
        assertTrue(sampled.size() <= times.size());
        assertEquals(LocalDate.of(2026, 1, 1), TimelineLayout.ticks(first, last).getFirst().at().toLocalDate());
        assertEquals("Jan 1, 2026 00:00", TimelineLayout.hoverLabel(first, 2));
        assertEquals("Jan 1, 2026", TimelineLayout.hoverLabel(first, 5));
    }

    @Test
    void compressedMonthsSkipTheEmptyGapBetweenHits() {
        LocalDateTime jan2025 = LocalDateTime.of(2025, 1, 15, 12, 0);
        LocalDateTime jan2026 = LocalDateTime.of(2026, 1, 15, 12, 0);
        List<java.time.YearMonth> months = List.of(
            java.time.YearMonth.of(2025, 1), java.time.YearMonth.of(2026, 1));
        assertEquals(0.25, TimelineLayout.compressedProgress(jan2025, months), 0.02);
        assertEquals(0.75, TimelineLayout.compressedProgress(jan2026, months), 0.02);
        int y2026 = TimelineLayout.yFromNewest(jan2026, jan2025, jan2026, months, 0, 100);
        int y2025 = TimelineLayout.yFromNewest(jan2025, jan2025, jan2026, months, 0, 100);
        assertTrue(y2026 < 50);
        assertTrue(y2025 > 50);
        LocalDateTime linearMid = TimelineLayout.timeFromNewest(0.5, jan2025, jan2026);
        assertEquals(2025, linearMid.getYear());
        LocalDateTime compressedMid = TimelineLayout.timeFromNewest(0.5, jan2025, jan2026, months);
        assertTrue(compressedMid.getYear() == 2025 || compressedMid.getYear() == 2026);
        assertTrue(compressedMid.getMonthValue() == 1);
        List<TimelineLayout.DateTick> ticks = TimelineLayout.ticks(months);
        assertEquals(2, ticks.size());
    }

    @Test
    void spacedTicksKeepAReadableGapOnANewestFirstTrack() {
        LocalDateTime oldest = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime newest = LocalDateTime.of(2026, 8, 1, 0, 0);
        List<TimelineLayout.DateTick> ticks = TimelineLayout.spacedTicks(oldest, newest, 80, 16);
        assertTrue(ticks.size() >= 2);
        int previousY = Integer.MIN_VALUE / 2;
        for (TimelineLayout.DateTick tick : ticks) {
            int y = TimelineLayout.yFromNewest(tick.at(), oldest, newest, 0, 80);
            assertTrue(y - previousY >= 16, () -> "ticks overlap at " + tick.label());
            previousY = y;
        }
    }
}
