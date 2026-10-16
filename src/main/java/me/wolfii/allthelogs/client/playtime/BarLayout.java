package me.wolfii.allthelogs.client.playtime;

/**
 * Horizontal placement of equally wide bars, in screen pixels. Every bar has the same width and bars touch.
 * Leftover pixels are split between both sides. When there are more columns than pixels, the middle
 * {@link #count} columns are shown, one pixel each.
 *
 * @param first    index of the leftmost shown column
 * @param count    number of shown columns
 * @param left     screen pixel where the first bar starts
 * @param barWidth width of every bar in screen pixels, at least one
 */
public record BarLayout(int first, int count, int left, int barWidth) {
    public static BarLayout of(int columns, int pixelLeft, int pixelWidth) {
        int shown = Math.max(0, Math.min(columns, pixelWidth));
        if (shown == 0) return new BarLayout(0, 0, pixelLeft, 1);
        int bar = pixelWidth / shown;
        return new BarLayout((columns - shown) / 2, shown, pixelLeft + (pixelWidth - bar * shown) / 2, bar);
    }

    /**
     * Column under the screen pixel {@code pixelX}, or -1 beside the bars.
     */
    public int columnAt(double pixelX) {
        if (count == 0 || pixelX < left) return -1;
        int offset = (int) Math.floor((pixelX - left) / barWidth);
        return offset >= count ? -1 : first + offset;
    }

    public int barLeft(int column) {
        return left + (column - first) * barWidth;
    }

    public int right() {
        return left + count * barWidth;
    }
}
