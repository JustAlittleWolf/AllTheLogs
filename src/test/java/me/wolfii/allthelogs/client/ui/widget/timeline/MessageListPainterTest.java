package me.wolfii.allthelogs.client.ui.widget.timeline;

import me.wolfii.allthelogs.client.ui.theme.Colors;
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
    void theMenuWashCoversTheWholeMessageIncludingWrappedLines() {
        var band = MessageListPainter.menuBand(4, 320, 100, 36);
        assertEquals(4, band.x());
        assertEquals(100, band.y());
        assertEquals(324, band.right());
        assertEquals(136, band.bottom());
        assertEquals(0x30FFFFFF, Colors.MENU_ROW);
    }

    @Test
    void aNarrowListClipsTheChipWithoutLeavingAGapOnTheRight() {
        var box = MessageListPainter.statusChipBounds(8, 30, 180, 200);
        assertEquals(30, box.width());
        assertEquals(8, box.x());
        assertEquals(8 + 30, box.x() + box.width());
    }
}
