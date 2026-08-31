package me.wolfii.allthelogs.client.ui.widget;

import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageWrap;
import me.wolfii.allthelogs.client.ui.text.MessageText;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import net.minecraft.client.gui.Font;

/**
 * Per-character prefix widths for the row currently being measured, cached across the wrap, selection, and
 * hit-testing queries of one frame so wrapping does not remeasure every prefix.
 */
final class RowWidths {
    private DisplayRow row;
    private Font font;
    private MessageWrap.RangeWidth widths;

    MessageWrap.RangeWidth of(DisplayRow row, Font font) {
        if (row == this.row && font == this.font && widths != null) {
            return widths;
        }
        String text = row.message();
        int[] formats = PackedFormatting.perChar(row.visualFormatting(), text.length());
        widths = MessageWrap.prefixWidths(text.length(), i ->
            font.width(MessageText.measureChar(text.charAt(i), formats[i])));
        this.row = row;
        this.font = font;
        return widths;
    }

    int width(DisplayRow row, Font font, int from, int to) {
        if (from >= to) return 0;
        return of(row, font).width(from, to);
    }
}
