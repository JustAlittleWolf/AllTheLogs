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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buckets filtered playtime into stacked columns. Stacking is oldest version first, then server name.
 * The legend keeps the ten series with the most playtime under the current filters.
 */
public final class PlaytimeChartModel {
    public static final int LEGEND_LIMIT = 10;
    public static final int PLOT_LEFT = 52;
    public static final int PLOT_TOP = 6;
    public static final int PLOT_RIGHT = 8;
    public static final int PLOT_BOTTOM = 18;

    private PlaytimeChartModel() {
    }

    public record HoverLine(String text, int color) {
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
        List<Integer> legend,
        List<Column> columns,
        double axisMax
    ) {
        public boolean isEmpty() {
            return columns.isEmpty();
        }

        public List<HoverLine> hover(int column, String unknownServer, String totalLabel) {
            if (column < 0 || column >= columns.size()) return List.of();
            Column current = columns.get(column);
            List<HoverLine> lines = new ArrayList<>();
            lines.add(new HoverLine(spanLabel(current.start(), current.end(), grain), 0xFFA0A0A0));
            for (int index = 0; index < series.size(); index++) {
                long bucket = current.millis()[index];
                double drawn = current.display()[index];
                if (bucket <= 0 && drawn <= 0) continue;
                PlaytimeSeries entry = series.get(index);
                String text = switch (style) {
                    case PERCENT -> entry.label(unknownServer) + "  " + percent(bucket, current.totalMillis())
                        + "  " + PlaytimeDuration.format(bucket);
                    case CUMULATIVE -> entry.label(unknownServer) + "  " + PlaytimeDuration.format(Math.round(drawn));
                    case BARS -> entry.label(unknownServer) + "  " + PlaytimeDuration.format(bucket);
                };
                lines.add(new HoverLine(text, entry.color()));
            }
            long total = style == PlaytimeStyle.CUMULATIVE ? Math.round(sum(current.display())) : current.totalMillis();
            if (lines.size() > 2) {
                lines.add(new HoverLine(totalLabel + "  " + PlaytimeDuration.format(total), 0xFFE8E8E8));
            } else if (lines.size() == 1) {
                lines.add(new HoverLine(PlaytimeDuration.format(0), 0xFFE8E8E8));
            }
            return List.copyOf(lines);
        }

        public Hit hit(double localX, double localY, Plot plot) {
            if (isEmpty() || plot.width() <= 0 || plot.height() <= 0) return null;
            if (localX < plot.x() || localX >= plot.x() + plot.width()) return null;
            if (localY < plot.y() || localY >= plot.y() + plot.height()) return null;
            int column = columnAt((localX - plot.x()) / plot.width());
            if (column < 0) return null;
            return new Hit(column, seriesAt(columns.get(column).display(), axisMax, localY, plot.y(), plot.height()));
        }

        private int columnAt(double fraction) {
            long start = epoch(viewStart);
            long end = epoch(viewEnd);
            if (end <= start) return columns.isEmpty() ? -1 : 0;
            long at = start + Math.round(Math.clamp(fraction, 0d, 1d) * (end - start));
            if (at >= end) at = end - 1;
            for (int index = 0; index < columns.size(); index++) {
                Column column = columns.get(index);
                long bucketStart = epoch(column.start());
                long bucketEnd = epoch(column.end());
                if (at >= bucketStart && at < bucketEnd) return index;
            }
            return -1;
        }
    }

    public static Plot plot(int width, int height) {
        return new Plot(PLOT_LEFT, PLOT_TOP,
            Math.max(1, width - PLOT_LEFT - PLOT_RIGHT),
            Math.max(1, height - PLOT_TOP - PLOT_BOTTOM));
    }

    public static Result build(List<PlaytimeSpan> spans, PlaytimeFilter filter, PlaytimeBreakdown breakdown,
                               PlaytimeStyle style, PlaytimeGrain grain, PlaytimeWindow view) {
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
        PlaytimeGrain resolved = PlaytimeGrain.resolve(grain, visible.start(), visible.end());
        List<PlaytimeSeries> series = series(kept, group);
        Map<PlaytimeSeries, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < series.size(); i++) index.put(series.get(i), i);
        List<LocalDateTime> buckets = buckets(domainStart, visible.end(), resolved);
        long[][] grid = new long[buckets.size()][series.size()];
        long[] seriesTotals = new long[series.size()];
        for (PlaytimeSpan span : kept) {
            int seriesIndex = index.get(PlaytimeSeries.of(group, span.version(), span.server()));
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
            List.copyOf(series), legend(seriesTotals), List.copyOf(columns), axisMax);
    }

    public static String spanLabel(LocalDateTime start, LocalDateTime end, PlaytimeGrain grain) {
        LocalDate first = start.toLocalDate();
        LocalDate last = end.minusNanos(1).toLocalDate();
        if (grain == PlaytimeGrain.MONTHS && first.getYear() == last.getYear() && first.getMonth() == last.getMonth()) {
            return String.format("%d-%02d", first.getYear(), first.getMonthValue());
        }
        if (first.equals(last)) return first.toString();
        return first + " - " + last;
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
            .thenComparing(String::compareToIgnoreCase));
        return List.copyOf(servers);
    }

    private static int seriesAt(double[] display, double axisMax, double localY, int plotY, int plotHeight) {
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
            case DAYS, AUTO -> date.atStartOfDay();
        };
    }

    static LocalDateTime advance(LocalDateTime bucketStart, PlaytimeGrain grain) {
        return switch (grain) {
            case WEEKS -> bucketStart.plusWeeks(1);
            case MONTHS -> bucketStart.plusMonths(1);
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

    private static List<PlaytimeSeries> series(List<PlaytimeSpan> spans, PlaytimeBreakdown breakdown) {
        Map<PlaytimeSeries, Boolean> found = new LinkedHashMap<>();
        for (PlaytimeSpan span : spans) {
            found.put(PlaytimeSeries.of(breakdown, span.version(), span.server()), Boolean.TRUE);
        }
        List<PlaytimeSeries> series = new ArrayList<>(found.keySet());
        series.sort(PlaytimeSeries.STACK_ORDER);
        return series;
    }

    private static List<Integer> legend(long[] totals) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < totals.length; index++) order.add(index);
        order.sort((left, right) -> {
            int byTime = Long.compare(totals[right], totals[left]);
            return byTime != 0 ? byTime : Integer.compare(left, right);
        });
        if (order.size() > LEGEND_LIMIT) return List.copyOf(order.subList(0, LEGEND_LIMIT));
        return List.copyOf(order);
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
