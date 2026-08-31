package me.wolfii.allthelogs.client.ui.widget;

import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.timeline.ScrubJump;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MessageTimelineTest {
    @Test
    void middleHoldLatchesOnlyAfterTheHoldThresholdWhileTheButtonIsDown() {
        assertFalse(AutoScroller.latchMiddleHold(false, true, 249));
        assertTrue(AutoScroller.latchMiddleHold(false, true, 250));
        assertFalse(AutoScroller.latchMiddleHold(false, false, 500));
        assertTrue(AutoScroller.latchMiddleHold(true, false, 500));
    }

    @Test
    void highlightMarkerExtendsOnePixelLeftAndStopsTwoPixelsEarly() {
        assertEquals(7, MessageListPainter.highlightLeft(8));
        assertEquals(MessageListLayout.ROW_HEIGHT - 2, MessageListPainter.highlightHeight());
    }

    @Test
    void previewScrubWaitsForThePreviousQueryAndTheThrottle() {
        ScrubJump first = new ScrubJump(LocalDateTime.of(2026, 1, 1, 0, 0), 0, 0.2);
        ScrubJump later = new ScrubJump(LocalDateTime.of(2026, 1, 2, 0, 0), 10, 0.8);
        assertTrue(ScrubDrag.shouldSendPreviewQuery(false, 100, 0, 100, first, null));
        assertFalse(ScrubDrag.shouldSendPreviewQuery(true, 200, 100, 100, later, first));
        assertFalse(ScrubDrag.shouldSendPreviewQuery(false, 199, 100, 100, later, first));
        assertTrue(ScrubDrag.shouldSendPreviewQuery(false, 200, 100, 100, later, first));
    }

    @Test
    void parkedThumbStillRequestsAfterTheThrottleWhenTheTargetMoved() {
        ScrubJump sent = new ScrubJump(LocalDateTime.of(2026, 1, 1, 0, 0), 0, 0.2);
        ScrubJump parked = new ScrubJump(LocalDateTime.of(2026, 1, 1, 12, 0), 5, 0.5);
        assertFalse(ScrubDrag.shouldSendPreviewQuery(false, 200, 100, 100, sent, sent));
        assertTrue(ScrubDrag.shouldSendPreviewQuery(false, 200, 100, 100, parked, sent));
        assertTrue(ScrubDrag.sameTarget(sent, new ScrubJump(sent.time(), sent.skip(), sent.progress())));
        assertFalse(ScrubDrag.sameTarget(sent, parked));
    }
}
