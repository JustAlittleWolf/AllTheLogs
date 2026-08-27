package me.wolfii.allthelogs.client.timeline;

import me.wolfii.allthelogs.data.MatchDay;
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
        assertEquals(first, TimelineLayout.timeFromOldest(0, first, last, List.of()));
        assertEquals(last, TimelineLayout.timeFromOldest(1, first, last, List.of()));
        assertEquals(0, TimelineLayout.yFromOldest(first, first, last, List.of(), 0, 100));
        assertEquals(99, TimelineLayout.yFromOldest(last, first, last, List.of(), 0, 100));
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
    void compressedDaysSkipTheEmptyGapBetweenHits() {
        LocalDateTime jan2025 = LocalDateTime.of(2025, 1, 15, 12, 0);
        LocalDateTime jan2026 = LocalDateTime.of(2026, 1, 15, 12, 0);
        List<LocalDate> days = List.of(LocalDate.of(2025, 1, 15), LocalDate.of(2026, 1, 15));
        assertEquals(0.25, TimelineLayout.compressedProgress(jan2025, days), 0.02);
        assertEquals(0.75, TimelineLayout.compressedProgress(jan2026, days), 0.02);
        int y2026 = TimelineLayout.yFromNewest(jan2026, jan2025, jan2026, days, 0, 100);
        int y2025 = TimelineLayout.yFromNewest(jan2025, jan2025, jan2026, days, 0, 100);
        assertTrue(y2026 < 50);
        assertTrue(y2025 > 50);
        LocalDateTime linearMid = TimelineLayout.timeFromNewest(0.5, jan2025, jan2026);
        assertEquals(2025, linearMid.getYear());
        LocalDateTime compressedMid = TimelineLayout.timeFromNewest(0.5, jan2025, jan2026, days);
        assertTrue(compressedMid.getYear() == 2025 || compressedMid.getYear() == 2026);
        assertTrue(compressedMid.getMonthValue() == 1);
        List<TimelineLayout.DateTick> ticks = TimelineLayout.ticks(days);
        assertEquals(2, ticks.size());
    }

    @Test
    void compressedDaysDoNotFillEmptyDaysInsideAMonth() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime last = LocalDateTime.of(2026, 1, 31, 12, 0);
        List<LocalDate> days = List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertEquals(0.25, TimelineLayout.compressedProgress(first, days), 0.02);
        assertEquals(0.75, TimelineLayout.compressedProgress(last, days), 0.02);
        LocalDateTime mid = TimelineLayout.timeFromOldest(0.5, first, last, days);
        assertTrue(mid.getDayOfMonth() == 1 || mid.getDayOfMonth() == 31);
        assertEquals(0, TimelineLayout.yFromOldest(first.toLocalDate().atStartOfDay(), first, last, days, 0, 100));
        assertTrue(TimelineLayout.yFromOldest(last, first, last, days, 0, 100) > 50);
    }

    @Test
    void matchDaysUseRealFirstAndLastTimesInsteadOfTheWholeClock() {
        LocalDateTime fiveAm = LocalDateTime.of(2026, 1, 1, 5, 0);
        LocalDateTime sixAm = LocalDateTime.of(2026, 1, 1, 6, 0);
        LocalDateTime nextDay = LocalDateTime.of(2026, 1, 2, 5, 0);
        List<MatchDay> days = List.of(
            new MatchDay(fiveAm.toLocalDate(), fiveAm, sixAm, 10),
            new MatchDay(nextDay.toLocalDate(), nextDay, nextDay, 4));
        assertEquals(0, TimelineLayout.matchDayProgress(fiveAm, days, 0), 0.0001);
        assertEquals(0.5 / 2, TimelineLayout.matchDayProgress(fiveAm.plusMinutes(30), days, 0), 0.0001);
        assertEquals(-1, TimelineLayout.skipFromProgress(0.1, days));
        assertEquals(-1, TimelineLayout.skipFromProgress(0.1, days.subList(0, 1)));
        assertEquals(10, TimelineLayout.skipFromProgress(0.5, days));
        assertEquals(13, TimelineLayout.skipFromProgress(1, days));
        assertEquals(fiveAm, TimelineLayout.timeFromMatchDays(0, days));
        assertEquals(nextDay, TimelineLayout.timeFromMatchDays(1, days));
        assertEquals(0, TimelineLayout.scrollForDateFraction(0, 400, 200, 0), 0.0001);
        assertEquals(200, TimelineLayout.scrollForDateFraction(0, 400, 200, 1), 0.0001);
        assertEquals(0, TimelineLayout.scrollToRow(0, 800, 200), 0.0001);
    }

    @Test
    void thumbFillsTheTrackWhenContentFitsAndPinsToTheEdgesWhenScrolling() {
        assertEquals(0, TimelineLayout.thumbHeight(200, 150, 200, 16));
        assertEquals(50, TimelineLayout.thumbHeight(200, 800, 200, 16));
        assertEquals(16, TimelineLayout.thumbHeight(200, 10_000, 200, 16));
        assertEquals(0, TimelineLayout.thumbOffset(200, 800, 200, 0, 50));
        assertEquals(150, TimelineLayout.thumbOffset(200, 800, 200, 600, 50));
        assertEquals(0, TimelineLayout.thumbOffset(200, 0, 50));
        assertEquals(150, TimelineLayout.thumbOffset(200, 1, 50));
        assertEquals(0.5, TimelineLayout.progressFromThumb(75, 200, 50), 0.0001);
        assertEquals(75, TimelineLayout.thumbOffset(200, TimelineLayout.progressFromThumb(75, 200, 50), 50));
        assertEquals(0, TimelineLayout.scrollToRow(0, 800, 200), 0.0001);
        assertEquals(200, TimelineLayout.scrollToRow(200, 800, 200), 0.0001);
        assertEquals(600, TimelineLayout.scrollToRow(10_000, 800, 200), 0.0001);
        assertEquals(8, TimelineLayout.thumbGrabOffset(18, 10, 20, 200), 0.0001);
        assertEquals(10, TimelineLayout.thumbGrabOffset(4, 10, 20, 200), 0.0001);
        int fewDays = TimelineLayout.thumbHeightForDays(200, 1);
        int someDays = TimelineLayout.thumbHeightForDays(200, 8);
        int manyDays = TimelineLayout.thumbHeightForDays(200, 40);
        assertTrue(fewDays > someDays);
        assertTrue(someDays > manyDays);
        assertEquals(16, manyDays);
        assertEquals(fewDays, TimelineLayout.thumbHeightForDays(200, 1));
        assertTrue(fewDays <= 40);
    }

    @Test
    void spacedTicksKeepAReadableGapOnAnOldestFirstTrack() {
        LocalDateTime oldest = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime newest = LocalDateTime.of(2026, 8, 1, 0, 0);
        List<TimelineLayout.DateTick> ticks = TimelineLayout.spacedTicks(oldest, newest, 80, 16);
        assertTrue(ticks.size() >= 2);
        int previousY = Integer.MIN_VALUE / 2;
        for (TimelineLayout.DateTick tick : ticks) {
            int y = TimelineLayout.yFromOldest(tick.at(), oldest, newest, List.of(), 0, 80);
            assertTrue(y - previousY >= 16, () -> "ticks overlap at " + tick.label());
            previousY = y;
        }
    }
}
