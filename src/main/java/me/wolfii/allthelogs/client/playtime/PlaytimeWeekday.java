package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Monday-to-Sunday totals and an hour-of-day heat map for the same filters and grouping as the timeline,
 * limited to an optional period. A span that crosses midnight or the full hour is split at that boundary.
 */
public final class PlaytimeWeekday {
    /** Heat map row that sums every weekday. Rows 0 to 6 are Monday to Sunday. */
    public static final int ALL_DAYS = 7;
    public static final int HOURS = 24;

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

    /**
     * @param heat playtime per heat map row ({@link #ALL_DAYS} last), hour of day, and series
     */
    public record Result(
        long totalMillis,
        PlaytimeBreakdown breakdown,
        List<PlaytimeSeries> series,
        List<PlaytimeChartModel.LegendEntry> legend,
        List<Bar> bars,
        double axisMax,
        long[][][] heat
    ) {
        public boolean isEmpty() {
            return totalMillis <= 0;
        }

        public long heatMillis(int row, int hour) {
            long total = 0;
            for (long value : heat[row][hour]) total += value;
            return total;
        }

        /**
         * Hover card for one bar: the day, its total, and each series in it.
         */
        public List<PlaytimeHover.Line> dayHover(int day, String totalLabel, String unknownServer, int focus) {
            Bar bar = bars.get(day);
            return hover(dayName(day), totalLabel, bar.millis(), unknownServer, focus);
        }

        /**
         * Hover card for one heat map cell.
         *
         * @param heading the row and hour, already formatted
         */
        public List<PlaytimeHover.Line> heatHover(int row, int hour, String heading, String totalLabel,
                                                  String unknownServer, int focus) {
            return hover(heading, totalLabel, heat[row][hour], unknownServer, focus);
        }

        private List<PlaytimeHover.Line> hover(String heading, String totalLabel, long[] millis, String unknownServer,
                                               int focus) {
            long total = 0;
            for (long value : millis) total += value;
            return PlaytimeHover.lines(heading, totalLabel, PlaytimeDuration.format(total), series,
                PlaytimeHover.weights(millis), index -> PlaytimeDuration.format(millis[index]), unknownServer, focus);
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

    /**
     * @param from  inclusive start of the period, or null for no lower bound
     * @param until exclusive end of the period, or null for no upper bound
     */
    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown,
                               LocalDateTime from, LocalDateTime until) {
        PlaytimeFilter active = filter == null ? PlaytimeFilter.all() : filter;
        PlaytimeBreakdown group = breakdown == null ? PlaytimeBreakdown.VERSION : breakdown;
        List<PlaytimeSpan> kept = new ArrayList<>();
        if (spans != null) {
            for (PlaytimeSpan span : spans) {
                if (!active.allows(span)) continue;
                PlaytimeSpan clipped = clip(span, from, until);
                if (clipped != null) kept.add(clipped);
            }
        }
        if (kept.isEmpty()) {
            return new Result(0, group, List.of(), List.of(), List.of(), 1, new long[ALL_DAYS + 1][HOURS][0]);
        }
        PlaytimeSeries.Index index = PlaytimeSeries.collect(kept, spans, group);
        int seriesCount = index.size();
        long[][][] heat = new long[ALL_DAYS + 1][HOURS][seriesCount];
        long total = 0;
        for (PlaytimeSpan span : kept) {
            int seriesIndex = index.of(span);
            LocalDateTime cursor = span.start();
            while (cursor.isBefore(span.end())) {
                LocalDateTime nextHour = cursor.withMinute(0).withSecond(0).withNano(0).plusHours(1);
                LocalDateTime to = span.end().isBefore(nextHour) ? span.end() : nextHour;
                long millis = Duration.between(cursor, to).toMillis();
                int day = cursor.getDayOfWeek().getValue() - 1;
                heat[day][cursor.getHour()][seriesIndex] += millis;
                heat[ALL_DAYS][cursor.getHour()][seriesIndex] += millis;
                total += millis;
                cursor = to;
            }
        }
        long[] seriesTotals = new long[seriesCount];
        List<Bar> bars = new ArrayList<>();
        double axisMax = 0;
        for (int day = 0; day < 7; day++) {
            long[] millis = new long[seriesCount];
            for (int hour = 0; hour < HOURS; hour++) {
                for (int seriesIndex = 0; seriesIndex < seriesCount; seriesIndex++) {
                    millis[seriesIndex] += heat[day][hour][seriesIndex];
                }
            }
            double[] display = new double[seriesCount];
            long dayTotal = 0;
            for (int seriesIndex = 0; seriesIndex < seriesCount; seriesIndex++) {
                display[seriesIndex] = millis[seriesIndex];
                dayTotal += millis[seriesIndex];
                seriesTotals[seriesIndex] += millis[seriesIndex];
            }
            axisMax = Math.max(axisMax, dayTotal);
            bars.add(new Bar(DayOfWeek.of(day + 1), millis, display));
        }
        if (axisMax <= 0) axisMax = 1;
        return new Result(total, group, index.series(), PlaytimeChartModel.legend(seriesTotals),
            List.copyOf(bars), axisMax, heat);
    }

    private static PlaytimeSpan clip(PlaytimeSpan span, LocalDateTime from, LocalDateTime until) {
        LocalDateTime start = from != null && span.start().isBefore(from) ? from : span.start();
        LocalDateTime end = until != null && span.end().isAfter(until) ? until : span.end();
        if (!end.isAfter(start)) return null;
        if (start.equals(span.start()) && end.equals(span.end())) return span;
        return new PlaytimeSpan(start, end, span.version(), span.server());
    }
}
