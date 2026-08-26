package me.wolfii.allthelogs.client.view;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 0 at {@code oldest}, 1 at {@code newest}. Marker lists may be in either order.
     */
    /**
     * Hover label for the timeline scrubber. Time is omitted when more than four distinct match dates are in
     * the current filter.
     */
    public static String hoverLabel(LocalDateTime time, int uniqueDates) {
        if (time == null) return "";
        return uniqueDates > 4 ? time.format(HOVER_DATE) : time.format(HOVER_DATE_TIME);
    }

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
        double fromNewest = 1 - progress(time, oldest, newest);
        return top + (int) Math.round(fromNewest * Math.max(0, height - 1));
    }

    public static LocalDateTime timeFromNewest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest) {
        LocalDateTime first = earlier(oldest, newest);
        LocalDateTime last = later(oldest, newest);
        if (first == null || last == null) return first;
        double clamped = Math.clamp(progressFromTop, 0, 1);
        long millis = Math.round(Duration.between(first, last).toMillis() * (1 - clamped));
        return first.plus(Duration.ofMillis(millis));
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
