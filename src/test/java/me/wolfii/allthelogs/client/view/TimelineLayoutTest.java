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

        List<LocalDateTime> times = List.of(first, first.plusHours(1), first.plusHours(2), last);
        List<LocalDateTime> sampled = TimelineLayout.downsample(times, 10, 20);
        assertEquals(first, sampled.getFirst());
        assertEquals(last, sampled.getLast());
        assertTrue(sampled.size() <= times.size());
        assertEquals(LocalDate.of(2026, 1, 1), TimelineLayout.ticks(first, last).getFirst().at().toLocalDate());
    }
}
