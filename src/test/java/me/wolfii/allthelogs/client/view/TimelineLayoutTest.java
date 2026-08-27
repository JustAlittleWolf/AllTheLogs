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
