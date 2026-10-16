package me.wolfii.allthelogs.client.ui.screen;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LatestMessagesPollTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    @Test
    void probeKeepsTheSlotUntilTheFullPageReturnsAndCanBeCancelledBetweenStages() {
        LatestMessagesPoll poll = new LatestMessagesPoll();
        assertTrue(poll.begin(0, true));
        assertTrue(poll.canApply(true));
        assertFalse(poll.begin(SECOND, true));
        poll.pause();
        assertFalse(poll.canApply(true));
        assertFalse(poll.finish(true));
        assertTrue(poll.begin(SECOND, true));
        assertTrue(poll.canApply(true));
        assertTrue(poll.finish(true));
    }

    @Test
    void pollsAtMostOncePerSecondAndNeverOverlapsSlowQueries() {
        LatestMessagesPoll poll = new LatestMessagesPoll();
        assertTrue(poll.begin(0, true));
        assertFalse(poll.begin(2 * SECOND, true));
        assertTrue(poll.finish(true));
        assertTrue(poll.begin(2 * SECOND, true));
        assertTrue(poll.finish(true));
        assertFalse(poll.begin(3 * SECOND - 1, true));
        assertTrue(poll.begin(3 * SECOND, true));
    }

    @Test
    void scrollingAwayDiscardsThePendingResultEvenAfterReturningToTheEnd() {
        LatestMessagesPoll poll = new LatestMessagesPoll();
        assertFalse(poll.begin(0, false));
        assertTrue(poll.begin(SECOND, true));
        assertFalse(poll.begin(SECOND + 1, false));
        assertFalse(poll.begin(2 * SECOND, true));
        assertFalse(poll.finish(true));
        assertTrue(poll.begin(2 * SECOND, true));
        assertTrue(poll.finish(true));
    }

    @Test
    void closingOrChangingFiltersInvalidatesTheResultWithoutFreeingTheQuerySlot() {
        LatestMessagesPoll poll = new LatestMessagesPoll();
        assertTrue(poll.begin(0, true));
        poll.pause();
        assertFalse(poll.begin(SECOND, true));
        assertFalse(poll.finish(true));
        assertTrue(poll.begin(SECOND, true));
        assertFalse(poll.finish(false));
        assertFalse(poll.finish(true));
    }
}
