package me.wolfii.allthelogs.client.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrubberGeometryTest {
    @Test
    void thumbHidesWhenContentFitsAndPinsToTheTrackEnds() {
        assertEquals(0, ScrubberGeometry.thumbOffset(200, 0, 50));
        assertEquals(150, ScrubberGeometry.thumbOffset(200, 1, 50));
        assertEquals(0.5, ScrubberGeometry.progressFromThumb(75, 200, 50), 0.0001);
        assertEquals(75, ScrubberGeometry.thumbOffset(200, ScrubberGeometry.progressFromThumb(75, 200, 50), 50));
        assertEquals(0, ScrubberGeometry.scrollToRow(0, 800, 200), 0.0001);
        assertEquals(200, ScrubberGeometry.scrollToRow(200, 800, 200), 0.0001);
        assertEquals(600, ScrubberGeometry.scrollToRow(10_000, 800, 200), 0.0001);
        assertEquals(8, ScrubberGeometry.thumbGrabOffset(18, 10, 20, 200), 0.0001);
        assertEquals(10, ScrubberGeometry.thumbGrabOffset(4, 10, 20, 200), 0.0001);
        assertEquals(0, ScrubberGeometry.scrollForDateFraction(0, 400, 200, 0), 0.0001);
        assertEquals(200, ScrubberGeometry.scrollForDateFraction(0, 400, 200, 1), 0.0001);
        assertEquals(1, ScrubberGeometry.pinnedProgress(0.4, false, true), 0.0001);
        assertEquals(0, ScrubberGeometry.pinnedProgress(0.4, true, false), 0.0001);
        assertEquals(0.4, ScrubberGeometry.pinnedProgress(0.4, false, false), 0.0001);
    }

    @Test
    void thumbHeightShrinksAsOccupiedDaysGrowAndHidesWhenThePageFits() {
        int fewDays = ScrubberGeometry.thumbHeightForDays(200, 1, 800, 200);
        int someDays = ScrubberGeometry.thumbHeightForDays(200, 8, 800, 200);
        int manyDays = ScrubberGeometry.thumbHeightForDays(200, 40, 800, 200);
        assertTrue(fewDays > someDays);
        assertTrue(someDays > manyDays);
        assertEquals(16, manyDays);
        assertTrue(fewDays <= 40);
        assertEquals(0, ScrubberGeometry.thumbHeightForDays(200, 8, 150, 200));
        assertEquals(someDays, ScrubberGeometry.thumbHeightForDays(200, 8, 800, 200));
    }
}
