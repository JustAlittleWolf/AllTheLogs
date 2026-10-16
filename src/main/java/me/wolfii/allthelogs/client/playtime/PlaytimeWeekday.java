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
 * Monday-to-Sunday totals and a time-of-day heat map for the same filters and grouping as the timeline,
 * limited to an optional period. The heat map uses slots of 1, 5, 15, or 60 minutes. A span that crosses
 * midnight or a slot boundary is split there.
 */
public final class PlaytimeWeekday {
    /** Heat map row that sums every weekday. Rows 0 to 6 are Monday to Sunday. */
    public static final int ALL_DAYS = 7;
    public static final int HOURS = 24;
    public static final int MINUTES_PER_DAY = 24 * 60;
    /** Heat map slot lengths on offer, in minutes. The first is the default. */
    public static final int[] SLOT_MINUTES = {60, 15, 5, 1};

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
     * @param slotMinutes length of one heat map slot
     * @param heat        playtime per heat map row ({@link #ALL_DAYS} last), slot of the day, and series
     */
    public record Result(
        long totalMillis,
        PlaytimeBreakdown breakdown,
        List<PlaytimeSeries> series,
        List<PlaytimeChartModel.LegendEntry> legend,
        List<Bar> bars,
        double axisMax,
        int slotMinutes,
        long[][][] heat
    ) {
        public boolean isEmpty() {
            return totalMillis <= 0;
        }

        public int slots() {
            return MINUTES_PER_DAY / slotMinutes;
        }

        /**
         * Playtime in one slot, of every series, or only {@code series} when it is not negative.
         */
        public long heatMillis(int row, int slot, int series) {
            if (series >= 0) return series < heat[row][slot].length ? heat[row][slot][series] : 0;
            long total = 0;
            for (long value : heat[row][slot]) total += value;
            return total;
        }

        /**
         * Start and end of a slot, such as {@code 14:15 - 14:30}.
         */
        public String slotLabel(int slot) {
            int start = slot * slotMinutes;
            int end = start + slotMinutes;
            return String.format("%02d:%02d - %02d:%02d", start / 60, start % 60, end / 60, end % 60);
        }

        /**
         * Hover card for one bar: the day, its total, and each series in it.
         */
        public List<PlaytimeHover.Line> dayHover(int day, String totalLabel, String unknownServer, int focus) {
            Bar bar = bars.get(day);
            return hover(dayName(day), totalLabel, bar.millis(), unknownServer, focus);
        }

        /**
         * Hover card for one heat map slot.
         *
         * @param heading the row and slot, already formatted
         */
        public List<PlaytimeHover.Line> heatHover(int row, int slot, String heading, String totalLabel,
                                                  String unknownServer, int focus) {
            return hover(heading, totalLabel, heat[row][slot], unknownServer, focus);
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

    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown,
                               LocalDateTime from, LocalDateTime until) {
        return build(spans, filter, breakdown, from, until, SLOT_MINUTES[0]);
    }

    /**
     * @param from        inclusive start of the period, or null for no lower bound
     * @param until       exclusive end of the period, or null for no upper bound
     * @param slotMinutes heat map slot length; must divide a day evenly
     */
    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown,
                               LocalDateTime from, LocalDateTime until, int slotMinutes) {
        int slotLength = slotMinutes > 0 && MINUTES_PER_DAY % slotMinutes == 0 ? slotMinutes : SLOT_MINUTES[0];
        int slots = MINUTES_PER_DAY / slotLength;
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
            return new Result(0, group, List.of(), List.of(), List.of(), 1, slotLength,
                new long[ALL_DAYS + 1][slots][0]);
        }
        PlaytimeSeries.Index index = PlaytimeSeries.collect(kept, spans, group);
        int seriesCount = index.size();
        long[][][] heat = new long[ALL_DAYS + 1][slots][seriesCount];
        long[][] dayMillis = new long[7][seriesCount];
        long total = 0;
        for (PlaytimeSpan span : kept) {
            int seriesIndex = index.of(span);
            LocalDateTime cursor = span.start();
            while (cursor.isBefore(span.end())) {
                int minuteOfDay = cursor.getHour() * 60 + cursor.getMinute();
                int slot = minuteOfDay / slotLength;
                LocalDateTime nextSlot = cursor.toLocalDate().atStartOfDay().plusMinutes((long) (slot + 1) * slotLength);
                LocalDateTime to = span.end().isBefore(nextSlot) ? span.end() : nextSlot;
                long millis = Duration.between(cursor, to).toMillis();
                int day = cursor.getDayOfWeek().getValue() - 1;
                heat[day][slot][seriesIndex] += millis;
                heat[ALL_DAYS][slot][seriesIndex] += millis;
                dayMillis[day][seriesIndex] += millis;
                total += millis;
                cursor = to;
            }
        }
        long[] seriesTotals = new long[seriesCount];
        List<Bar> bars = new ArrayList<>();
        double axisMax = 0;
        for (int day = 0; day < 7; day++) {
            long[] millis = dayMillis[day];
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
            List.copyOf(bars), axisMax, slotLength, heat);
    }

    private static PlaytimeSpan clip(PlaytimeSpan span, LocalDateTime from, LocalDateTime until) {
        LocalDateTime start = from != null && span.start().isBefore(from) ? from : span.start();
        LocalDateTime end = until != null && span.end().isAfter(until) ? until : span.end();
        if (!end.isAfter(start)) return null;
        if (start.equals(span.start()) && end.equals(span.end())) return span;
        return span.withStartAndEnd(start, end);
    }
}
