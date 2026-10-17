package me.wolfii.allthelogs.client.ui.widget.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageListPainterTest {
    @Test
    void theCountChipSitsFlushWithTheRightEdgeOfTheMessageList() {
        var box = MessageListPainter.statusChipBounds(20, 400, 90, 200);
        assertEquals(20 + 400, box.x() + box.width());
    }

    @Test
    void theDefaultWidthIsHalfTheTimedSampleAndGrowsOnlyForAWiderLabel() {
        int sample = 200;
        int padding = 16;
        int floor = MessageListPainter.statusChipBounds(0, 400, 40, sample).width();
        assertEquals(sample / 2 + padding, floor);

        var grown = MessageListPainter.statusChipBounds(10, 400, 180, sample);
        assertEquals(180 + padding, grown.width());
        assertEquals(10 + 400, grown.x() + grown.width());
    }

    @Test
    void aNarrowListClipsTheChipWithoutLeavingAGapOnTheRight() {
        var box = MessageListPainter.statusChipBounds(8, 30, 180, 200);
        assertEquals(30, box.width());
        assertEquals(8, box.x());
        assertEquals(8 + 30, box.x() + box.width());
    }
}
