package me.wolfii.allthelogs.client.ui.widget;

import org.junit.jupiter.api.Test;

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
}
