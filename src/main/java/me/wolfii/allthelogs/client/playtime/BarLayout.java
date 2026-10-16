package me.wolfii.allthelogs.client.playtime;

import java.util.Arrays;

/**
 * Horizontal placement of bars in screen pixels, with no gaps. Bars are all equally wide when that leaves
 * at most a tenth of the width unused, split between both sides. Otherwise bars share the whole width and
 * differ by at most one pixel. With more columns than pixels, several columns fall on one pixel, and the
 * one with the most playtime is drawn there.
 *
 * @param edges screen pixel where each bar starts, plus the end of the last bar
 */
public record BarLayout(int[] edges) {
    private static final double MAX_UNUSED = 0.1;

    public static BarLayout of(int columns, int pixelLeft, int pixelWidth) {
        int count = Math.max(0, columns);
        int[] edges = new int[count + 1];
        if (count == 0 || pixelWidth <= 0) {
            Arrays.fill(edges, pixelLeft);
            return new BarLayout(edges);
        }
        int bar = pixelWidth / count;
        if (bar >= 1 && pixelWidth - bar * count <= pixelWidth * MAX_UNUSED) {
            int left = pixelLeft + (pixelWidth - bar * count) / 2;
            for (int index = 0; index <= count; index++) edges[index] = left + index * bar;
        } else {
            for (int index = 0; index <= count; index++) {
                edges[index] = pixelLeft + (int) ((long) index * pixelWidth / count);
            }
        }
        return new BarLayout(edges);
    }

    public int count() {
        return edges.length - 1;
    }

    public int left() {
        return edges[0];
    }

    public int right() {
        return edges[edges.length - 1];
    }

    public int barLeft(int column) {
        return edges[column];
    }

    public int barRight(int column) {
        return edges[column + 1];
    }

    /**
     * Column covering the screen pixel {@code pixelX}, or -1 beside the bars. Where several columns share a
     * pixel, the first of them; {@link #drawnAt} picks the one that is actually drawn.
     */
    public int columnAt(double pixelX) {
        if (count() == 0 || pixelX < left() || pixelX >= right()) return -1;
        int low = 0;
        int high = count() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (edges[mid] <= pixelX) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    /**
     * For each screen pixel from {@link #left()} to {@link #right()}, the column drawn there: the covering
     * column, or among several columns on the same pixel the one with the largest {@code totals}.
     */
    public int[] drawnAt(double[] totals) {
        int[] drawn = new int[Math.max(0, right() - left())];
        Arrays.fill(drawn, -1);
        for (int column = 0; column < count(); column++) {
            int from = edges[column] - left();
            int to = Math.max(from + 1, edges[column + 1] - left());
            for (int x = from; x < to && x < drawn.length; x++) {
                int current = drawn[x];
                if (current < 0 || totals[column] > totals[current]) drawn[x] = column;
            }
        }
        return drawn;
    }
}
