package me.wolfii.allthelogs.client.ui.widget;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTimelineTest {
    @Test
    void middleHoldLatchesOnlyAfterTheHoldThresholdWhileTheButtonIsDown() {
        assertFalse(MessageTimeline.latchMiddleHold(false, true, 249));
        assertTrue(MessageTimeline.latchMiddleHold(false, true, 250));
        assertFalse(MessageTimeline.latchMiddleHold(false, false, 500));
        assertTrue(MessageTimeline.latchMiddleHold(true, false, 500));
    }

    @Test
    void clickInTopHalfUsesTheClickedRowHalf() {
        assertTrue(MessageTimeline.clickInTopHalf(0, 0, 12));
        assertTrue(MessageTimeline.clickInTopHalf(5, 0, 12));
        assertFalse(MessageTimeline.clickInTopHalf(6, 0, 12));
        assertFalse(MessageTimeline.clickInTopHalf(11, 0, 12));
    }

    @Test
    void highlightMarkerExtendsOnePixelLeftAndStopsTwoPixelsEarly() {
        assertEquals(7, MessageTimeline.highlightLeft(8));
        assertEquals(MessageTimeline.ROW_HEIGHT - 2, MessageTimeline.highlightHeight());
    }

    @Test
    void previewScrubWaitsForThePreviousQueryAndTheThrottle() {
        MessageTimeline.ScrubJump first = new MessageTimeline.ScrubJump(LocalDateTime.of(2026, 1, 1, 0, 0), 0, 0.2);
        MessageTimeline.ScrubJump later = new MessageTimeline.ScrubJump(LocalDateTime.of(2026, 1, 2, 0, 0), 10, 0.8);
        assertTrue(MessageTimeline.shouldSendPreviewScrubQuery(false, 100, 0, 100, first, null));
        assertFalse(MessageTimeline.shouldSendPreviewScrubQuery(true, 200, 100, 100, later, first));
        assertFalse(MessageTimeline.shouldSendPreviewScrubQuery(false, 199, 100, 100, later, first));
        assertTrue(MessageTimeline.shouldSendPreviewScrubQuery(false, 200, 100, 100, later, first));
    }

    @Test
    void parkedThumbStillRequestsAfterTheThrottleWhenTheTargetMoved() {
        MessageTimeline.ScrubJump sent = new MessageTimeline.ScrubJump(LocalDateTime.of(2026, 1, 1, 0, 0), 0, 0.2);
        MessageTimeline.ScrubJump parked = new MessageTimeline.ScrubJump(LocalDateTime.of(2026, 1, 1, 12, 0), 5, 0.5);
        assertFalse(MessageTimeline.shouldSendPreviewScrubQuery(false, 200, 100, 100, sent, sent));
        assertTrue(MessageTimeline.shouldSendPreviewScrubQuery(false, 200, 100, 100, parked, sent));
        assertTrue(MessageTimeline.sameScrubTarget(sent, new MessageTimeline.ScrubJump(sent.time(), sent.skip(), sent.progress())));
        assertFalse(MessageTimeline.sameScrubTarget(sent, parked));
    }
}
