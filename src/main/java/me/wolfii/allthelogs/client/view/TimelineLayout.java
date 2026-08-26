package me.wolfii.allthelogs.client.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps match timestamps onto a vertical timeline. Mapping is O(1) in the number of matches: the browser
 * stores only the oldest and newest hit, so drawing the scrubber never walks the match list.
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
     * Milliseconds from the UTC epoch. Used so per-frame mapping does not allocate {@link java.time.Duration}.
     */
    public static long epochMillis(LocalDateTime time) {
        if (time == null) return 0;
        return time.toEpochSecond(ZoneOffset.UTC) * 1000L + time.getNano() / 1_000_000L;
    }

    /**
     * 0 at {@code oldest}, 1 at {@code newest}. The two range ends may be in either order.
     */
    public static double progress(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest) {
        return progressMillis(epochMillis(time), epochMillis(earlier(oldest, newest)), epochMillis(later(oldest, newest)));
    }

    public static double progressMillis(long timeMs, long oldestMs, long newestMs) {
        long first = Math.min(oldestMs, newestMs);
        long last = Math.max(oldestMs, newestMs);
        long span = last - first;
        if (span <= 0) return 0;
        long at = timeMs - first;
        if (at <= 0) return 0;
        if (at >= span) return 1;
        return at / (double) span;
    }

    /**
     * Pixel Y with oldest at the top. Prefer {@link #yFromNewest} for the log browser, which matches
     * newest-first message order.
     */
    public static int y(LocalDateTime time, LocalDateTime first, LocalDateTime last, int top, int height) {
        return yFromProgress(progress(time, first, last), top, height);
    }

    /**
     * Pixel Y with newest at the top and oldest at the bottom, like Immich's timeline scrubber.
     */
    public static int yFromNewest(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest, int top, int height) {
        return yFromNewestMillis(epochMillis(time), epochMillis(oldest), epochMillis(newest), top, height);
    }

    public static int yFromNewestMillis(long timeMs, long oldestMs, long newestMs, int top, int height) {
        return yFromProgress(1 - progressMillis(timeMs, oldestMs, newestMs), top, height);
    }

    public static LocalDateTime timeFromNewest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest) {
        LocalDateTime first = earlier(oldest, newest);
        LocalDateTime last = later(oldest, newest);
        if (first == null || last == null) return first;
        return timeFromNewestMillis(progressFromTop, epochMillis(first), epochMillis(last));
    }

    public static LocalDateTime timeFromNewestMillis(double progressFromTop, long oldestMs, long newestMs) {
        long first = Math.min(oldestMs, newestMs);
        long last = Math.max(oldestMs, newestMs);
        double clamped = Math.clamp(progressFromTop, 0, 1);
        long millis = Math.round((last - first) * (1 - clamped));
        return LocalDateTime.ofEpochSecond(
            Math.floorDiv(first + millis, 1000L),
            (int) (Math.floorMod(first + millis, 1000L) * 1_000_000L),
            ZoneOffset.UTC);
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

    public static List<DateTick> ticks(LocalDateTime first, LocalDateTime last) {
        if (first == null || last == null) return List.of();
        LocalDateTime oldest = earlier(first, last);
        LocalDateTime newest = later(first, last);
        LocalDate start = oldest.toLocalDate();
        LocalDate end = newest.toLocalDate();
        if (end.isBefore(start)) return List.of();
        long days = ChronoUnit.DAYS.between(start, end) + 1;
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

    private static int yFromProgress(double progress, int top, int height) {
        return top + (int) Math.round(progress * Math.max(0, height - 1));
    }

    public record DateTick(LocalDateTime at, String label) {
    }
}
