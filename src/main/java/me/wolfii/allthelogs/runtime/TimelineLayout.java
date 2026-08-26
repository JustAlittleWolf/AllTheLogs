package me.wolfii.allthelogs.runtime;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps match timestamps onto a vertical timeline and chooses date labels for the track.
 */
public final class TimelineLayout {
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");

    private TimelineLayout() {
    }

    public static double progress(LocalDateTime time, LocalDateTime first, LocalDateTime last) {
        if (time == null || first == null || last == null) return 0;
        if (!last.isAfter(first)) return 0;
        double total = Duration.between(first, last).toMillis();
        if (total <= 0) return 0;
        double at = Duration.between(first, time).toMillis();
        if (at < 0) return 0;
        if (at > total) return 1;
        return at / total;
    }

    public static int y(LocalDateTime time, LocalDateTime first, LocalDateTime last, int top, int height) {
        return top + (int) Math.round(progress(time, first, last) * Math.max(0, height - 1));
    }

    public static List<DateTick> ticks(LocalDateTime first, LocalDateTime last) {
        if (first == null || last == null) return List.of();
        LocalDate start = first.toLocalDate();
        LocalDate end = last.toLocalDate();
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
            ticks.add(new DateTick(first, first.format(DAY)));
        }
        return List.copyOf(ticks);
    }

    /**
     * Downsamples markers so neighbouring timestamps are at least {@code minGapPx} apart on a track of
     * {@code height} pixels. The first and last markers are always kept.
     */
    public static List<LocalDateTime> downsample(List<LocalDateTime> times, int height, int minGapPx) {
        if (times.size() <= 2 || height <= 0 || minGapPx <= 0) return List.copyOf(times);
        LocalDateTime first = times.getFirst();
        LocalDateTime last = times.getLast();
        List<LocalDateTime> kept = new ArrayList<>();
        int lastY = Integer.MIN_VALUE;
        for (int i = 0; i < times.size(); i++) {
            LocalDateTime time = times.get(i);
            int y = y(time, first, last, 0, height);
            boolean isEdge = i == 0 || i == times.size() - 1;
            if (isEdge || y - lastY >= minGapPx) {
                kept.add(time);
                lastY = y;
            }
        }
        return List.copyOf(kept);
    }

    public record DateTick(LocalDateTime at, String label) {
    }
}
