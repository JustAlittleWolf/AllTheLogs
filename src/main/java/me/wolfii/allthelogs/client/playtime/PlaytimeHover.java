package me.wolfii.allthelogs.client.playtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Hover card text shared by the playtime charts: the period, the total, the series under the pointer when
 * there is one, an empty line, and then every other series with playtime, largest first. When only one
 * series has playtime, the card shows the period and that series alone. Names are left-aligned and values
 * right-aligned.
 */
public final class PlaytimeHover {
    public static final int HEADING = 0xFFA0A0A0;
    public static final int TOTAL = 0xFFE8E8E8;

    private PlaytimeHover() {
    }

    /**
     * @param value right-aligned text, or empty for a line that is only a label
     */
    public record Line(String label, String value, int color, boolean bold) {
        public static Line plain(String label, int color) {
            return new Line(label, "", color, false);
        }
    }

    /**
     * @param weights   per series; a series is listed when its weight is above zero and ordered by it
     * @param valueText text in the right column, by series index
     * @param focus     series under the pointer, shown right below the total instead of in the list, or -1
     */
    public static List<Line> lines(String heading, String totalLabel, String total, List<PlaytimeSeries> series,
                                   double[] weights, IntFunction<String> valueText, String unknownServer, int focus) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.plain(heading, HEADING));
        int only = onlySeries(weights, series.size());
        if (only >= 0) {
            lines.add(entry(series.get(only), valueText.apply(only), unknownServer, false));
            return List.copyOf(lines);
        }
        lines.add(new Line(totalLabel, total, TOTAL, false));
        boolean focused = focus >= 0 && focus < series.size() && focus < weights.length && weights[focus] > 0;
        if (focused) lines.add(entry(series.get(focus), valueText.apply(focus), unknownServer, true));
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < series.size() && index < weights.length; index++) {
            if (weights[index] > 0 && !(focused && index == focus)) order.add(index);
        }
        if (order.isEmpty()) return List.copyOf(lines);
        order.sort((left, right) -> {
            int byWeight = Double.compare(weights[right], weights[left]);
            return byWeight != 0 ? byWeight : Integer.compare(left, right);
        });
        lines.add(Line.plain("", TOTAL));
        for (int index : order) lines.add(entry(series.get(index), valueText.apply(index), unknownServer, false));
        return List.copyOf(lines);
    }

    /**
     * The one series with playtime, or -1 when none or several have some.
     */
    private static int onlySeries(double[] weights, int count) {
        int found = -1;
        for (int index = 0; index < count && index < weights.length; index++) {
            if (weights[index] <= 0) continue;
            if (found >= 0) return -1;
            found = index;
        }
        return found;
    }

    private static Line entry(PlaytimeSeries series, String value, String unknownServer, boolean bold) {
        return new Line(series.shortLabel(unknownServer), value, series.color(), bold);
    }

    public static double[] weights(long[] millis) {
        double[] weights = new double[millis.length];
        for (int index = 0; index < millis.length; index++) weights[index] = millis[index];
        return weights;
    }
}
