package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Buckets filtered playtime into stacked columns. Stacking is oldest version first, then server name.
 * The legend lists every series, most playtime first.
 */
public final class PlaytimeChartModel {
    public static final int PLOT_LEFT = 52;
    public static final int PLOT_TOP = 8;
    public static final int PLOT_RIGHT = 8;
    public static final int PLOT_BOTTOM = 18;

    private PlaytimeChartModel() {
    }

    public record Column(LocalDateTime start, LocalDateTime end, long[] millis, double[] display) {
        public Column {
            millis = millis.clone();
            display = display.clone();
        }

        public long totalMillis() {
            long total = 0;
            for (long value : millis) total += value;
            return total;
        }
    }

    public record Hit(int column, int series) {
    }

    /**
     * One legend row: the series index and that series' playtime under the current filters.
     */
    public record LegendEntry(int series, long millis) {
    }

    public record Plot(int x, int y, int width, int height) {
    }

    public record Result(
        long totalMillis,
        LocalDateTime domainStart,
        LocalDateTime domainEnd,
        LocalDateTime viewStart,
        LocalDateTime viewEnd,
        PlaytimeGrain grain,
        PlaytimeStyle style,
        PlaytimeBreakdown breakdown,
        List<PlaytimeSeries> series,
        List<LegendEntry> legend,
        List<Column> columns,
        double axisMax
    ) {
        public boolean isEmpty() {
            return columns.isEmpty();
        }

        public List<PlaytimeHover.Line> hover(int column, String unknownServer, String totalLabel, int focus) {
            if (column < 0 || column >= columns.size()) return List.of();
            Column current = columns.get(column);
            boolean cumulative = style == PlaytimeStyle.CUMULATIVE;
            long total = cumulative ? Math.round(sum(current.display())) : current.totalMillis();
            double[] weights = cumulative ? current.display() : PlaytimeHover.weights(current.millis());
            return PlaytimeHover.lines(spanLabel(current.start(), current.end(), grain), totalLabel,
                PlaytimeDuration.format(total), series, weights, index -> switch (style) {
                    case PERCENT -> percent(current.millis()[index], current.totalMillis())
                        + "  " + PlaytimeDuration.format(current.millis()[index]);
                    case CUMULATIVE -> PlaytimeDuration.format(Math.round(current.display()[index]));
                    case BARS -> PlaytimeDuration.format(current.millis()[index]);
                }, unknownServer, focus);
        }

        /**
         * Section of {@code column} at {@code localY}, or null outside the plot height.
         */
        public Hit hit(int column, double localY, Plot plot) {
            if (column < 0 || column >= columns.size() || plot.height() <= 0) return null;
            if (localY < plot.y() || localY >= plot.y() + plot.height()) return null;
            return new Hit(column, seriesAt(columns.get(column).display(), axisMax, localY, plot.y(), plot.height()));
        }

        /**
         * Position of the screen pixel {@code pixelX} inside the view, from 0 at {@link #viewStart} to 1 at
         * {@link #viewEnd}. Zooming keeps the time under the pointer fixed with it.
         */
        public double fractionAt(BarLayout layout, double pixelX) {
            long start = epoch(viewStart);
            long end = epoch(viewEnd);
            if (end <= start || layout.count() == 0) return 0.5;
            double clampedX = Math.clamp(pixelX, layout.left(), layout.right() - 0.001);
            int column = layout.columnAt(clampedX);
            if (column < 0 || column >= columns.size()) return 0.5;
            Column bucket = columns.get(column);
            int barWidth = Math.max(1, layout.barRight(column) - layout.barLeft(column));
            double within = Math.clamp((clampedX - layout.barLeft(column)) / barWidth, 0d, 1d);
            long bucketStart = epoch(bucket.start());
            double at = bucketStart + within * (epoch(bucket.end()) - bucketStart);
            return Math.clamp((at - start) / (end - start), 0d, 1d);
        }
    }

    public static Plot plot(int width, int height) {
        return plot(width, height, PLOT_LEFT);
    }

    /**
     * @param left room for the value labels left of the plot
     */
    public static Plot plot(int width, int height, int left) {
        return plot(width, height, left, PLOT_TOP);
    }

    public static Plot plot(int width, int height, int left, int top) {
        return new Plot(left, top,
            Math.max(1, width - left - PLOT_RIGHT),
            Math.max(1, height - top - PLOT_BOTTOM));
    }

    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown,
                               PlaytimeStyle style, PlaytimeGrain grain, PlaytimeWindow view) {
        return build(spans, filter, breakdown, style, grain, view, 0);
    }

    /**
     * @param chartWidth plot width in GUI pixels, used by {@link PlaytimeGrain#AUTO}, or 0 when unknown
     */
    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown,
                               PlaytimeStyle style, PlaytimeGrain grain, PlaytimeWindow view, int chartWidth) {
        PlaytimeFilter active = filter == null ? PlaytimeFilter.all() : filter;
        PlaytimeBreakdown group = breakdown == null ? PlaytimeBreakdown.VERSION : breakdown;
        PlaytimeStyle drawn = style == null ? PlaytimeStyle.BARS : style;
        List<PlaytimeSpan> kept = new ArrayList<>();
        long total = 0;
        LocalDateTime domainStart = null;
        LocalDateTime domainEnd = null;
        if (spans != null) {
            for (PlaytimeSpan span : spans) {
                if (!active.allows(span)) continue;
                kept.add(span);
                total += span.millis();
                if (domainStart == null || span.start().isBefore(domainStart)) domainStart = span.start();
                if (domainEnd == null || span.end().isAfter(domainEnd)) domainEnd = span.end();
            }
        }
        if (kept.isEmpty() || domainStart == null || !domainEnd.isAfter(domainStart)) {
            return new Result(0, null, null, null, null, PlaytimeGrain.DAYS, drawn, group,
                List.of(), List.of(), List.of(), 1);
        }
        PlaytimeWindow domain = new PlaytimeWindow(domainStart, domainEnd);
        PlaytimeWindow visible = PlaytimeWindow.clamp(domain, view);
        PlaytimeGrain resolved = PlaytimeGrain.resolve(grain, visible.start(), visible.end(), chartWidth);
        if (resolved == PlaytimeGrain.YEARS && !severalYears(domainStart, domainEnd)) resolved = PlaytimeGrain.MONTHS;
        PlaytimeSeries.Index index = PlaytimeSeries.collect(kept, spans, group);
        List<PlaytimeSeries> series = index.series();
        List<LocalDateTime> buckets = buckets(domainStart, visible.end(), resolved);
        long[][] grid = new long[buckets.size()][series.size()];
        long[] seriesTotals = new long[series.size()];
        for (PlaytimeSpan span : kept) {
            int seriesIndex = index.of(span);
            seriesTotals[seriesIndex] += span.millis();
            addSpan(grid, buckets, resolved, span, seriesIndex);
        }
        List<Column> columns = new ArrayList<>();
        long[] running = new long[series.size()];
        double axisMax = drawn == PlaytimeStyle.PERCENT ? 1 : 0;
        for (int bucket = 0; bucket < buckets.size(); bucket++) {
            for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                running[seriesIndex] += grid[bucket][seriesIndex];
            }
            LocalDateTime start = buckets.get(bucket);
            LocalDateTime end = advance(start, resolved);
            if (!overlaps(start, end, visible.start(), visible.end())) continue;
            long bucketTotal = 0;
            for (long value : grid[bucket]) bucketTotal += value;
            double[] display = new double[series.size()];
            if (drawn == PlaytimeStyle.CUMULATIVE) {
                long stacked = 0;
                for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                    display[seriesIndex] = running[seriesIndex];
                    stacked += running[seriesIndex];
                }
                axisMax = Math.max(axisMax, stacked);
            } else if (drawn == PlaytimeStyle.PERCENT) {
                if (bucketTotal > 0) {
                    for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                        display[seriesIndex] = grid[bucket][seriesIndex] / (double) bucketTotal;
                    }
                }
            } else {
                for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
                    display[seriesIndex] = grid[bucket][seriesIndex];
                }
                axisMax = Math.max(axisMax, bucketTotal);
            }
            columns.add(new Column(start, end, grid[bucket], display));
        }
        if (axisMax <= 0) axisMax = 1;
        return new Result(total, domainStart, domainEnd, visible.start(), visible.end(), resolved, drawn, group,
            series, legend(seriesTotals), List.copyOf(columns), axisMax);
    }

    public static String spanLabel(LocalDateTime start, LocalDateTime end, PlaytimeGrain grain) {
        LocalDate first = start.toLocalDate();
        LocalDate last = end.minusNanos(1).toLocalDate();
        if (grain == PlaytimeGrain.YEARS && first.getYear() == last.getYear()) return Integer.toString(first.getYear());
        if (grain == PlaytimeGrain.MONTHS && first.getYear() == last.getYear() && first.getMonth() == last.getMonth()) {
            return String.format("%d-%02d", first.getYear(), first.getMonthValue());
        }
        if (first.equals(last)) return first.toString();
        return first + " - " + last;
    }

    /**
     * Whether {@code spans} cover more than one calendar year, so a yearly timeline has more than one bar.
     */
    public static boolean spansSeveralYears(List<PlaytimeSpan> spans) {
        if (spans == null || spans.isEmpty()) return false;
        LocalDateTime first = spans.getFirst().start();
        LocalDateTime last = spans.getFirst().end();
        for (PlaytimeSpan span : spans) {
            if (span.start().isBefore(first)) first = span.start();
            if (span.end().isAfter(last)) last = span.end();
        }
        return severalYears(first, last);
    }

    private static boolean severalYears(LocalDateTime start, LocalDateTime end) {
        return start.getYear() != end.minusNanos(1).getYear();
    }

    /**
     * Short label under a bar: the first day of a week, otherwise the same as {@link #spanLabel}.
     */
    public static String axisLabel(LocalDateTime start, LocalDateTime end, PlaytimeGrain grain) {
        if (grain == PlaytimeGrain.WEEKS) return start.toLocalDate().toString();
        return spanLabel(start, end, grain);
    }

    public static List<String> versionsIn(List<PlaytimeSpan> spans) {
        List<String> versions = new ArrayList<>();
        if (spans == null) return List.of();
        for (PlaytimeSpan span : spans) {
            if (!versions.contains(span.version())) versions.add(span.version());
        }
        return MinecraftVersions.newestFirst(versions);
    }

    public static List<String> serversIn(List<PlaytimeSpan> spans) {
        List<String> servers = new ArrayList<>();
        if (spans == null) return List.of();
        for (PlaytimeSpan span : spans) {
            String key = PlaytimeFilter.serverKey(span.server());
            if (!servers.contains(key)) servers.add(key);
        }
        servers.sort(Comparator.comparing((String server) -> server.equals(PlaytimeFilter.UNKNOWN_SERVER))
            .thenComparing(PlaytimeSeries::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(servers);
    }

    /**
     * Series whose stacked section is at {@code localY}, or -1 above the stack.
     */
    public static int seriesAt(double[] display, double axisMax, double localY, int plotY, int plotHeight) {
        if (axisMax <= 0 || plotHeight <= 0) return -1;
        double stack = 0;
        for (double value : display) stack += Math.max(0, value);
        if (stack <= 0) return -1;
        double fromBottom = (plotY + plotHeight - localY) / plotHeight * axisMax;
        if (fromBottom < 0 || fromBottom > stack) return -1;
        double cursor = 0;
        int chosen = -1;
        for (int index = 0; index < display.length; index++) {
            if (display[index] <= 0) continue;
            cursor += display[index];
            chosen = index;
            if (fromBottom <= cursor) return index;
        }
        return chosen;
    }

    private static void addSpan(long[][] grid, List<LocalDateTime> buckets, PlaytimeGrain grain, PlaytimeSpan span,
                                int seriesIndex) {
        if (buckets.isEmpty()) return;
        int bucket = bucketIndex(buckets, span.start());
        while (bucket < buckets.size()) {
            LocalDateTime start = buckets.get(bucket);
            LocalDateTime end = advance(start, grain);
            if (!start.isBefore(span.end())) break;
            long overlap = overlapMillis(span.start(), span.end(), start, end);
            if (overlap > 0) grid[bucket][seriesIndex] += overlap;
            bucket++;
        }
    }

    private static int bucketIndex(List<LocalDateTime> buckets, LocalDateTime time) {
        int low = 0;
        int high = buckets.size() - 1;
        int found = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (!buckets.get(mid).isAfter(time)) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    private static List<LocalDateTime> buckets(LocalDateTime domainStart, LocalDateTime viewEnd, PlaytimeGrain grain) {
        List<LocalDateTime> buckets = new ArrayList<>();
        LocalDateTime cursor = floor(domainStart, grain);
        int guard = 0;
        while (cursor.isBefore(viewEnd) && guard++ < 20_000) {
            buckets.add(cursor);
            LocalDateTime next = advance(cursor, grain);
            if (!next.isAfter(cursor)) break;
            cursor = next;
        }
        return buckets;
    }

    static LocalDateTime floor(LocalDateTime time, PlaytimeGrain grain) {
        LocalDate date = time.toLocalDate();
        return switch (grain) {
            case WEEKS -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case MONTHS -> date.withDayOfMonth(1).atStartOfDay();
            case YEARS -> date.withDayOfYear(1).atStartOfDay();
            case DAYS, AUTO -> date.atStartOfDay();
        };
    }

    static LocalDateTime advance(LocalDateTime bucketStart, PlaytimeGrain grain) {
        return switch (grain) {
            case WEEKS -> bucketStart.plusWeeks(1);
            case MONTHS -> bucketStart.plusMonths(1);
            case YEARS -> bucketStart.plusYears(1);
            case DAYS, AUTO -> bucketStart.plusDays(1);
        };
    }

    private static boolean overlaps(LocalDateTime start, LocalDateTime end, LocalDateTime viewStart, LocalDateTime viewEnd) {
        return start.isBefore(viewEnd) && end.isAfter(viewStart);
    }

    private static long overlapMillis(LocalDateTime spanStart, LocalDateTime spanEnd, LocalDateTime bucketStart,
                                      LocalDateTime bucketEnd) {
        LocalDateTime from = spanStart.isAfter(bucketStart) ? spanStart : bucketStart;
        LocalDateTime to = spanEnd.isBefore(bucketEnd) ? spanEnd : bucketEnd;
        if (!to.isAfter(from)) return 0;
        return Duration.between(from, to).toMillis();
    }

    static List<LegendEntry> legend(long[] totals) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < totals.length; index++) order.add(index);
        order.sort((left, right) -> {
            int byTime = Long.compare(totals[right], totals[left]);
            return byTime != 0 ? byTime : Integer.compare(left, right);
        });
        List<LegendEntry> ranked = new ArrayList<>(order.size());
        for (int series : order) ranked.add(new LegendEntry(series, totals[series]));
        return List.copyOf(ranked);
    }

    private static String percent(long part, long total) {
        if (total <= 0) return "0%";
        return Math.round(100.0 * part / total) + "%";
    }

    private static double sum(double[] values) {
        double total = 0;
        for (double value : values) total += value;
        return total;
    }

    private static long epoch(LocalDateTime time) {
        return time.toEpochSecond(ZoneOffset.UTC);
    }
}
