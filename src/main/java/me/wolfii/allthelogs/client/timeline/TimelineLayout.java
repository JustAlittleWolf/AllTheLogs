package me.wolfii.allthelogs.client.timeline;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps match timestamps onto a vertical timeline and chooses date labels for the track.
 */
public final class TimelineLayout {
    private static final DateTimeFormatter HOVER_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US);
    private static final DateTimeFormatter HOVER_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private TimelineLayout() {
    }

    /**
     * Hover label for the timeline scrubber. Time is omitted when more than four distinct match dates are in
     * the current filter.
     */
    public static String hoverLabel(LocalDateTime time, int uniqueDates) {
        if (time == null) return "";
        return uniqueDates > 4 ? time.format(HOVER_DATE) : time.format(HOVER_DATE_TIME);
    }

    /**
     * 0 at {@code oldest}, 1 at {@code newest}. Marker lists may be in either order.
     */
    public static double progress(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest) {
        LocalDateTime first = earlier(oldest, newest);
        LocalDateTime last = later(oldest, newest);
        if (time == null || first == null || last == null) return 0;
        if (!last.isAfter(first)) return 0;
        double total = Duration.between(first, last).toMillis();
        if (total <= 0) return 0;
        double at = Duration.between(first, time).toMillis();
        if (at < 0) return 0;
        if (at > total) return 1;
        return at / total;
    }

    /**
     * Pixel Y with oldest at the top. Prefer {@link #yFromNewest} for the log browser, which matches
     * newest-first message order.
     */
    public static int y(LocalDateTime time, LocalDateTime first, LocalDateTime last, int top, int height) {
        return top + (int) Math.round(progress(time, first, last) * Math.max(0, height - 1));
    }

    /**
     * Pixel Y with newest at the top and oldest at the bottom, like Immich's timeline scrubber.
     */
    public static int yFromNewest(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest, int top, int height) {
        return yFromNewest(time, oldest, newest, List.of(), top, height);
    }

    /**
     * Pixel Y with newest at the top. When {@code months} lists the months that actually contain matches,
     * empty months between them are omitted and each listed month gets an equal share of the track.
     */
    public static int yFromNewest(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest,
                                  List<YearMonth> months, int top, int height) {
        double fromNewest = 1 - progress(time, oldest, newest, months);
        return top + (int) Math.round(fromNewest * Math.max(0, height - 1));
    }

    public static LocalDateTime timeFromNewest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest) {
        return timeFromNewest(progressFromTop, oldest, newest, List.of());
    }

    public static LocalDateTime timeFromNewest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest,
                                               List<YearMonth> months) {
        if (months != null && months.size() >= 2) {
            return timeFromCompressedMonths(progressFromTop, months);
        }
        LocalDateTime first = earlier(oldest, newest);
        LocalDateTime last = later(oldest, newest);
        if (first == null || last == null) return first;
        double clamped = Math.clamp(progressFromTop, 0, 1);
        long millis = Math.round(Duration.between(first, last).toMillis() * (1 - clamped));
        return first.plus(Duration.ofMillis(millis));
    }

    public static double progress(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest,
                                  List<YearMonth> months) {
        if (months != null && months.size() >= 2) {
            return compressedProgress(time, months);
        }
        return progress(time, oldest, newest);
    }

    /**
     * 0 at the start of the oldest listed month, 1 at the end of the newest. Each month occupies the same
     * fraction of the track, so a year with no matches between two hits does not take space.
     */
    public static double compressedProgress(LocalDateTime time, List<YearMonth> months) {
        if (time == null || months == null || months.isEmpty()) return 0;
        YearMonth target = YearMonth.from(time);
        int index = 0;
        for (int i = 0; i < months.size(); i++) {
            YearMonth month = months.get(i);
            if (month.equals(target)) {
                index = i;
                break;
            }
            if (month.isBefore(target)) {
                index = i;
            } else {
                break;
            }
        }
        YearMonth month = months.get(index);
        double fraction = 0;
        if (month.equals(target)) {
            LocalDateTime start = month.atDay(1).atStartOfDay();
            LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
            double total = Duration.between(start, end).toMillis();
            fraction = total <= 0 ? 0 : Duration.between(start, time).toMillis() / total;
            fraction = Math.clamp(fraction, 0, 1);
        } else if (target.isAfter(month)) {
            fraction = 1;
        }
        return (index + fraction) / months.size();
    }

    static LocalDateTime timeFromCompressedMonths(double progressFromTop, List<YearMonth> months) {
        double fromOldest = 1 - Math.clamp(progressFromTop, 0, 1);
        double scaled = fromOldest * months.size();
        int index = Math.min(months.size() - 1, (int) Math.floor(scaled));
        if (index < 0) index = 0;
        double fraction = Math.clamp(scaled - index, 0, 1);
        if (index == months.size() - 1 && fromOldest >= 1) {
            fraction = 1;
        }
        YearMonth month = months.get(index);
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        long millis = Math.round(Duration.between(start, end).toMillis() * fraction);
        return start.plus(Duration.ofMillis(millis));
    }

    public static LocalDateTime oldest(List<LocalDateTime> times) {
        LocalDateTime oldest = null;
        for (LocalDateTime time : times) {
            if (time != null && (oldest == null || time.isBefore(oldest))) oldest = time;
        }
        return oldest;
    }

    public static LocalDateTime newest(List<LocalDateTime> times) {
        LocalDateTime newest = null;
        for (LocalDateTime time : times) {
            if (time != null && (newest == null || time.isAfter(newest))) newest = time;
        }
        return newest;
    }

    static LocalDateTime earlier(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    /**
     * One label per occupied month when there are a handful of them, otherwise years. Newest-at-top
     * placement is done by {@link #spacedTicks}.
     */
    public static List<DateTick> ticks(List<YearMonth> months) {
        if (months == null || months.isEmpty()) return List.of();
        boolean multiYear = months.getFirst().getYear() != months.getLast().getYear();
        if (months.size() > 36) {
            List<DateTick> ticks = new ArrayList<>();
            Integer previousYear = null;
            for (YearMonth month : months) {
                if (previousYear != null && previousYear == month.getYear()) continue;
                ticks.add(new DateTick(month.atDay(1).atStartOfDay(), month.format(YEAR)));
                previousYear = month.getYear();
            }
            return List.copyOf(ticks);
        }
        DateTimeFormatter format = multiYear || months.size() > 4 ? MONTH : DateTimeFormatter.ofPattern("MMM", Locale.US);
        List<DateTick> ticks = new ArrayList<>(months.size());
        for (YearMonth month : months) {
            ticks.add(new DateTick(month.atDay(1).atStartOfDay(), month.format(format)));
        }
        return List.copyOf(ticks);
    }

    public static List<DateTick> ticks(LocalDateTime first, LocalDateTime last) {
        if (first == null || last == null) return List.of();
        LocalDateTime oldest = earlier(first, last);
        LocalDateTime newest = later(first, last);
        LocalDate start = oldest.toLocalDate();
        LocalDate end = newest.toLocalDate();
        if (end.isBefore(start)) return List.of();
        long days = Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays();
        List<DateTick> ticks = new ArrayList<>();
        if (days > 400) {
            LocalDate cursor = LocalDate.of(start.getYear(), 1, 1);
            if (cursor.isBefore(start)) cursor = cursor.plusYears(1);
            while (!cursor.isAfter(end)) {
                ticks.add(new DateTick(cursor.atStartOfDay(), cursor.format(YEAR)));
                cursor = cursor.plusYears(1);
            }
        } else if (days > 45) {
            LocalDate cursor = LocalDate.of(start.getYear(), start.getMonth(), 1);
            if (cursor.isBefore(start)) cursor = cursor.plusMonths(1);
            while (!cursor.isAfter(end)) {
                ticks.add(new DateTick(cursor.atStartOfDay(), cursor.format(MONTH)));
                cursor = cursor.plusMonths(1);
            }
        } else if (days > 1) {
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                ticks.add(new DateTick(cursor.atStartOfDay(), cursor.format(DAY)));
                cursor = cursor.plusDays(Math.max(1, days / 6));
            }
        } else {
            ticks.add(new DateTick(oldest, oldest.format(DAY)));
        }
        return List.copyOf(ticks);
    }

    /**
     * Date labels placed along a newest-at-top track, spaced at least {@code minGapPx} apart so they stay readable.
     */
    public static List<DateTick> spacedTicks(LocalDateTime oldest, LocalDateTime newest, int height, int minGapPx) {
        return spacedTicks(oldest, newest, List.of(), height, minGapPx);
    }

    public static List<DateTick> spacedTicks(LocalDateTime oldest, LocalDateTime newest, List<YearMonth> months,
                                             int height, int minGapPx) {
        List<DateTick> raw = months != null && months.size() >= 2 ? ticks(months) : ticks(oldest, newest);
        if (raw.isEmpty() || height <= 0 || minGapPx <= 0) return raw;
        List<DateTick> ordered = new ArrayList<>(raw);
        ordered.sort((a, b) -> Integer.compare(
            yFromNewest(a.at(), oldest, newest, months, 0, height),
            yFromNewest(b.at(), oldest, newest, months, 0, height)));
        List<DateTick> kept = new ArrayList<>();
        int lastY = Integer.MIN_VALUE / 2;
        for (DateTick tick : ordered) {
            int y = yFromNewest(tick.at(), oldest, newest, months, 0, height);
            if (kept.isEmpty() || Math.abs(y - lastY) >= minGapPx) {
                kept.add(tick);
                lastY = y;
            }
        }
        DateTick last = ordered.getLast();
        int lastTickY = yFromNewest(last.at(), oldest, newest, months, 0, height);
        if (!kept.getLast().equals(last) && Math.abs(lastTickY - lastY) >= minGapPx) {
            kept.add(last);
        }
        return List.copyOf(kept);
    }

    /**
     * Downsamples markers so neighbouring timestamps are at least {@code minGapPx} apart on a track of
     * {@code height} pixels. The first and last markers are always kept.
     */
    public static List<LocalDateTime> downsample(List<LocalDateTime> times, int height, int minGapPx) {
        if (times.size() <= 2 || height <= 0 || minGapPx <= 0) return List.copyOf(times);
        LocalDateTime first = oldest(times);
        LocalDateTime last = newest(times);
        List<LocalDateTime> kept = new ArrayList<>();
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime time = times.get(i);
            int y = yFromNewest(time, first, last, 0, height);
            boolean isEdge = i == 0 || i == times.size() - 1;
            if (isEdge || Math.abs(y - lastY) >= minGapPx) {
                kept.add(time);
                lastY = y;
            }
        }
        return List.copyOf(kept);
    }

    public record DateTick(LocalDateTime at, String label) {
    }
}
