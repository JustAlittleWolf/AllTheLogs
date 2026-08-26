package me.wolfii.allthelogs.client.view;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineLayoutTest {
    @Test
    void timelineProgressMapsEndsAndMidpoint() {
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
        assertEquals(LocalDate.of(2026, 1, 1), TimelineLayout.ticks(first, last).getFirst().at().toLocalDate());
        assertEquals("Jan 1, 2026 00:00", TimelineLayout.hoverLabel(first, 2));
        assertEquals("Jan 1, 2026", TimelineLayout.hoverLabel(first, 5));
    }

    @Test
    void millisMappingMatchesLocalDateTimeMapping() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime last = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime mid = LocalDateTime.of(2026, 3, 17, 12, 30);
        long firstMs = TimelineLayout.epochMillis(first);
        long lastMs = TimelineLayout.epochMillis(last);
        long midMs = TimelineLayout.epochMillis(mid);
        assertEquals(TimelineLayout.progress(mid, first, last), TimelineLayout.progressMillis(midMs, firstMs, lastMs), 1e-12);
        assertEquals(
            TimelineLayout.yFromNewest(mid, first, last, 10, 400),
            TimelineLayout.yFromNewestMillis(midMs, firstMs, lastMs, 10, 400));
    }

    @Test
    void mappingASingleTimestampDoesNotWalkAMatchList() {
        LocalDateTime first = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime last = LocalDateTime.of(2026, 1, 1, 0, 0);
        long firstMs = TimelineLayout.epochMillis(first);
        long lastMs = TimelineLayout.epochMillis(last);
        long start = System.nanoTime();
        int checksum = 0;
        for (int i = 0; i < 200_000; i++) {
            checksum += TimelineLayout.yFromNewestMillis(firstMs + i * 1000L, firstMs, lastMs, 0, 800);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs < 250, "O(1) mapping took " + elapsedMs + "ms");
        assertTrue(checksum != 0);
    }
}
