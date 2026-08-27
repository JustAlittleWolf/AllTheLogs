package me.wolfii.allthelogs.client.ui.widget;

import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.ui.text.MessageText;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * One frame's worth of message-list geometry: where the widget is, how far it is scrolled, and the laid-out
 * rows inside it. Screen and content coordinates are converted here so painting and hit-testing cannot drift
 * apart.
 * <p>
 * Content y is measured from the top of the laid-out rows; screen y is measured from the top of the window.
 * When the content is shorter than the viewport it is bottom-aligned, which is what {@link #contentOrigin()}
 * accounts for.
 *
 * @param x       left edge of the whole widget, list plus timeline track
 * @param y       top edge of the whole widget
 * @param width   width of the whole widget, list plus timeline track
 * @param height  height of the whole widget
 * @param scrollY content-y currently at the top of the viewport
 */
record ListView(int x, int y, int width, int height, double scrollY,
                MessageListLayout layout, List<DisplayRow> rows, Font font) {
    /** Padding between the list edge and the timestamp gutter. */
    static final int PAD = 4;

    static int listWidth(int width) {
        return Math.max(0, width - MessageTimeline.TIMELINE_WIDTH);
    }

    static int timestampWidth(Font font) {
        return font.width(MessageText.TIMESTAMP_GUTTER);
    }

    /**
     * Pixel width available to message text, for a widget of {@code width}.
     */
    static int messageWidth(int width, Font font) {
        return Math.max(16, listWidth(width) - PAD * 2 - timestampWidth(font));
    }

    /** Width of the list, excluding the timeline track on the right. */
    int listWidth() {
        return listWidth(width);
    }

    int timestampWidth() {
        return timestampWidth(font);
    }

    int messageWidth() {
        return messageWidth(width, font);
    }

    /** Widget-local x where message text starts, after the timestamp gutter. */
    int messageLocalX() {
        return PAD + timestampWidth();
    }

    int messageX() {
        return x + messageLocalX();
    }

    /**
     * Top padding that bottom-aligns the content when it is shorter than the viewport.
     */
    int contentOrigin() {
        return MessageListLayout.bottomPad(layout.contentHeight(), height);
    }

    int screenY(int contentY) {
        return y + contentOrigin() + contentY - (int) Math.round(scrollY);
    }

    int contentY(double localY) {
        return (int) Math.round(localY + scrollY - contentOrigin());
    }

    /**
     * Index of the row under widget-local {@code localY}, or {@code -1} when the pointer is in the gap
     * between rows or outside the content.
     */
    int rowAt(double localY) {
        if (layout.size() == 0) return -1;
        int contentY = contentY(localY);
        int index = layout.rowAtY(contentY);
        if (index < 0 || index >= rows.size()) return -1;
        int top = layout.rowY(index);
        if (contentY < top || contentY >= top + layout.rowHeight(index)) return -1;
        return index;
    }

    /**
     * Index of the first row at least partly visible.
     */
    int firstVisibleRow() {
        return Math.max(0, layout.rowAtY(scrollY - contentOrigin()));
    }

    /**
     * Index of the last row at least partly visible.
     */
    int lastVisibleRow() {
        if (layout.size() == 0) return 0;
        return Math.max(firstVisibleRow(), layout.rowAtY(scrollY - contentOrigin() + Math.max(0, height)));
    }

    boolean containsScreen(int screenX, int screenY) {
        return screenX >= x && screenX < x + listWidth() && screenY >= y && screenY < y + height;
    }
}
