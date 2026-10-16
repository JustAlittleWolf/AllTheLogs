package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monday-to-Sunday totals for the same filters and grouping as the timeline. A span that crosses midnight
 * is split across the two days.
 */
public final class PlaytimeWeekday {
    private PlaytimeWeekday() {
    }

    public record Bar(DayOfWeek day, long[] millis, double[] display) {
        public Bar {
            millis = millis.clone();
            display = display.clone();
        }

        public long totalMillis() {
            long total = 0;
            for (long value : millis) total += value;
            return total;
        }
    }

    public record Result(
        long totalMillis,
        PlaytimeBreakdown breakdown,
        List<PlaytimeSeries> series,
        List<PlaytimeChartModel.LegendEntry> legend,
        List<Bar> bars,
        int mostDay,
        int leastDay,
        double axisMax
    ) {
        public boolean isEmpty() {
            return totalMillis <= 0;
        }
    }

    /**
     * Full weekday name in the system locale. {@code mondayIndex} is 0 for Monday through 6 for Sunday.
     */
    public static String dayName(int mondayIndex) {
        return DayOfWeek.of(mondayIndex + 1).getDisplayName(TextStyle.FULL, Locale.getDefault());
    }

    /**
     * The first three characters of {@link #dayName(int)}, or the whole name when it is shorter.
     */
    public static String shortDayName(int mondayIndex) {
        String name = dayName(mondayIndex);
        return name.length() <= 3 ? name : name.substring(0, 3);
    }

    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown) {
        PlaytimeFilter active = filter == null ? PlaytimeFilter.all() : filter;
        PlaytimeBreakdown group = breakdown == null ? PlaytimeBreakdown.VERSION : breakdown;
        List<PlaytimeSpan> kept = new ArrayList<>();
        if (spans != null) {
            for (PlaytimeSpan span : spans) {
                if (active.allows(span)) kept.add(span);
            }
        }
        if (kept.isEmpty()) {
            return new Result(0, group, List.of(), List.of(), List.of(), 0, 0, 1);
        }
        Map<PlaytimeSeries, Boolean> found = new LinkedHashMap<>();
        for (PlaytimeSpan span : kept) {
            found.put(PlaytimeSeries.of(group, span.version(), span.server()), Boolean.TRUE);
        }
        List<PlaytimeSeries> series = new ArrayList<>(found.keySet());
        series.sort(PlaytimeSeries.STACK_ORDER);
        Map<PlaytimeSeries, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < series.size(); i++) index.put(series.get(i), i);
        long[][] grid = new long[7][series.size()];
        long total = 0;
        for (PlaytimeSpan span : kept) {
            int seriesIndex = index.get(PlaytimeSeries.of(group, span.version(), span.server()));
            LocalDateTime cursor = span.start();
            while (cursor.isBefore(span.end())) {
                LocalDateTime midnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
                LocalDateTime to = span.end().isBefore(midnight) ? span.end() : midnight;
                long millis = java.time.Duration.between(cursor, to).toMillis();
                int day = cursor.getDayOfWeek().getValue() - 1;
                grid[day][seriesIndex] += millis;
                total += millis;
                cursor = to;
            }
        }
        long[] dayTotals = new long[7];
        List<Bar> bars = new ArrayList<>();
        double axisMax = 0;
        int most = 0;
        int least = 0;
        for (int day = 0; day < 7; day++) {
            double[] display = new double[series.size()];
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                display[seriesIndex] = grid[day][seriesIndex];
                dayTotals[day] += grid[day][seriesIndex];
            }
            axisMax = Math.max(axisMax, dayTotals[day]);
            if (dayTotals[day] > dayTotals[most]) most = day;
            if (dayTotals[day] < dayTotals[least]) least = day;
            bars.add(new Bar(DayOfWeek.of(day + 1), grid[day], display));
        }
        if (axisMax <= 0) axisMax = 1;
        long[] seriesTotals = new long[series.size()];
        for (int day = 0; day < 7; day++) {
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                seriesTotals[seriesIndex] += grid[day][seriesIndex];
            }
        }
        return new Result(total, group, List.copyOf(series), PlaytimeChartModel.legend(seriesTotals),
            List.copyOf(bars), most, least, axisMax);
    }
}
