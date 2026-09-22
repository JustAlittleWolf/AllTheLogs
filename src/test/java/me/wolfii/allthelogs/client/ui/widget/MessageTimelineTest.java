package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.CursorStyle;
import me.wolfii.allthelogs.client.timeline.ScrubJump;
import me.wolfii.allthelogs.client.timeline.ScrubberGeometry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MessageTimelineTest {
    @Test
    void middleDragAndTheTimelineUseAnUpDownCursor() {
        assertEquals(CursorStyle.VERTICAL_RESIZE, MessageTimeline.listCursor(true, true, true));
        assertEquals(CursorStyle.HAND, MessageTimeline.listCursor(false, true, true));
        assertEquals(CursorStyle.TEXT, MessageTimeline.listCursor(false, false, true));
        assertEquals(CursorStyle.POINTER, MessageTimeline.listCursor(false, false, false));
    }

    @Test
    void middleHoldLatchesOnlyAfterTheHoldThresholdWhileTheButtonIsDown() {
        assertFalse(AutoScroller.latchMiddleHold(false, true, 249));
        assertTrue(AutoScroller.latchMiddleHold(false, true, 250));
        assertFalse(AutoScroller.latchMiddleHold(false, false, 500));
        assertTrue(AutoScroller.latchMiddleHold(true, false, 500));
    }

    @Test
    void highlightMarkerExtendsOnePixelLeft() {
        assertEquals(7, MessageListPainter.highlightLeft(8));
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
        assertFalse(ScrubDrag.sameTarget(sent, parked));
    }

    @Test
    void rightClickDoesNotClearTheSelection() {
        assertFalse(MessageTimeline.clearsSelectionOnMouseDown(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        assertFalse(MessageTimeline.clearsSelectionOnMouseDown(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT));
    }

    @Test
    void leftClickOnAMessageDismissesTheContextMenu() {
        assertTrue(MessageTimeline.dismissesContextMenuOnMouseDown(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertTrue(MessageTimeline.dismissesContextMenuOnMouseDown(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        assertFalse(MessageTimeline.dismissesContextMenuOnMouseDown(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT));
    }

    @Test
    void resizeKeepsTheBottomPinnedWhenTheViewportWasAtTheEnd() {
        assertFalse(MessageTimeline.pinToBottomOnResize(0, 400, -1));
        assertTrue(MessageTimeline.pinToBottomOnResize(200, 400, 200));
        assertTrue(MessageTimeline.pinToBottomOnResize(0, 120, 200));
        assertFalse(MessageTimeline.pinToBottomOnResize(40, 400, 200));
    }

    @Test
    void searchStayAnchorIsTwoThirdsDownTheViewport() {
        assertEquals(2.0 / 3.0, MessageTimeline.VIEWPORT_STAY_FRACTION, 0.0001);
        assertEquals(140, ListView.contentYAtFraction(40, 0, 150, MessageTimeline.VIEWPORT_STAY_FRACTION), 0.0001);
        assertEquals(40, ListView.contentYAtFraction(40, 0, 150, 0), 0.0001);
        assertEquals(190, ListView.contentYAtFraction(40, 0, 150, 1), 0.0001);
        assertEquals(20, ListView.contentYAtFraction(40, 20, 150, 0), 0.0001);
        int stayY = (int) Math.round(ListView.contentYAtFraction(40, 0, 150, MessageTimeline.VIEWPORT_STAY_FRACTION));
        assertEquals(40, ScrubberGeometry.scrollToRow(stayY, 800, 150, MessageTimeline.VIEWPORT_STAY_FRACTION), 0.0001);
    }
}
