package me.wolfii.allthelogs.client.playtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Hover card text shared by the playtime charts: a heading, the total, an empty line, and then each series
 * with playtime, largest first.
 */
public final class PlaytimeHover {
    public static final int HEADING = 0xFFA0A0A0;
    public static final int TOTAL = 0xFFE8E8E8;

    private PlaytimeHover() {
    }

    public record Line(String text, int color) {
    }

    /**
     * @param weights  per series; a series is listed when its weight is above zero and ordered by it
     * @param valueText text after the series label, by series index
     */
    public static List<Line> lines(String heading, String total, List<PlaytimeSeries> series, double[] weights,
                                   IntFunction<String> valueText, String unknownServer) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(heading, HEADING));
        lines.add(new Line(total, TOTAL));
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < series.size() && index < weights.length; index++) {
            if (weights[index] > 0) order.add(index);
        }
        if (order.isEmpty()) return List.copyOf(lines);
        order.sort((left, right) -> {
            int byWeight = Double.compare(weights[right], weights[left]);
            return byWeight != 0 ? byWeight : Integer.compare(left, right);
        });
        lines.add(new Line("", TOTAL));
        for (int index : order) {
            PlaytimeSeries entry = series.get(index);
            lines.add(new Line(entry.shortLabel(unknownServer) + "  " + valueText.apply(index), entry.color()));
        }
        return List.copyOf(lines);
    }

    public static double[] weights(long[] millis) {
        double[] weights = new double[millis.length];
        for (int index = 0; index < millis.length; index++) weights[index] = millis[index];
        return weights;
    }
}
