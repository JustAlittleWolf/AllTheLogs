package me.wolfii.allthelogs.client.timeline;

import me.wolfii.allthelogs.data.MatchDay;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Maps match timestamps onto 0–1 progress along a vertical timeline, and back.
 * <p>
 * Three scales exist, from coarsest to most precise. {@link #linearProgress} spreads wall-clock time evenly, so
 * long stretches without matches still take up track. {@link #dayProgress} gives every occupied calendar day an
 * equal share and positions within a day by clock time. {@link #matchDayProgress} also gives every occupied day
 * an equal share, but positions within a day between that day's real first and last match, which is what the
 * scrubber uses. A day whose matches all share one timestamp is {@linkplain MatchDay#collapsed() collapsed} and
 * has to be addressed by match rank instead; see {@link #skipAtProgress}.
 */
public final class TimelineScale {
    private TimelineScale() {
    }

    /**
     * 0 at the earlier bound, 1 at the later one. The bounds may be passed in either order.
     */
    public static double linearProgress(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest) {
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
     * Timestamp at {@code progressFromTop} on the {@link #linearProgress} scale.
     */
    public static LocalDateTime timeAtLinearProgress(double progressFromTop, LocalDateTime oldest,
                                                     LocalDateTime newest) {
        LocalDateTime first = earlier(oldest, newest);
        LocalDateTime last = later(oldest, newest);
        if (first == null || last == null) return first;
        return interpolate(first, last, progressFromTop);
    }

    /**
     * Pixel Y for a 0–1 progress, with oldest at {@code top} and newest at the bottom, matching
     * chronological message order.
     */
    public static int yAtProgress(double progress, int top, int height) {
        return top + (int) Math.round(Math.clamp(progress, 0, 1) * Math.max(0, height - 1));
    }

    /**
     * 0 at the start of the oldest listed day, 1 at the end of the newest. Each listed day occupies the same
     * fraction of the track, so days without matches take no space. Within a day, progress follows clock time
     * from midnight to midnight.
     */
    public static double dayProgress(LocalDateTime time, List<LocalDate> days) {
        if (time == null || days == null || days.isEmpty()) return 0;
        LocalDate target = time.toLocalDate();
        int index = 0;
        for (int i = 0; i < days.size(); i++) {
            LocalDate day = days.get(i);
            if (day.equals(target)) {
                index = i;
                break;
            }
            if (day.isBefore(target)) {
                index = i;
            } else {
                break;
            }
        }
        LocalDate day = days.get(index);
        double fraction = 0;
        if (day.equals(target)) {
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            double total = Duration.between(start, end).toMillis();
            fraction = total <= 0 ? 0 : Duration.between(start, time).toMillis() / total;
            fraction = Math.clamp(fraction, 0, 1);
        } else if (target.isAfter(day)) {
            fraction = 1;
        }
        return (index + fraction) / days.size();
    }

    /**
     * Occupied-day progress using each day's real first and last match times, so a day is not stretched
     * across hours that hold nothing. {@code collapsedFraction} positions within a day that has no time span.
     */
    public static double matchDayProgress(LocalDateTime time, List<MatchDay> days, double collapsedFraction) {
        if (time == null || days == null || days.isEmpty()) return 0;
        int index = dayIndex(time.toLocalDate(), days);
        MatchDay day = days.get(index);
        return (index + dayFraction(time, day, collapsedFraction)) / days.size();
    }

    /**
     * Timestamp at {@code progressFromTop} on the {@link #matchDayProgress} scale, or {@code null} when there
     * are no occupied days.
     */
    public static LocalDateTime timeAtProgress(double progressFromTop, List<MatchDay> days) {
        if (days == null || days.isEmpty()) return null;
        int index = dayIndexAtProgress(progressFromTop, days);
        MatchDay day = days.get(index);
        if (day.collapsed()) return day.oldest();
        return interpolate(day.oldest(), day.newest(), fractionInDay(progressFromTop, days));
    }

    /**
     * Match rank to skip to when the day at {@code progressFromTop} is collapsed onto one timestamp, or
     * {@code -1} when that day can be reached by time instead.
     */
    public static long skipAtProgress(double progressFromTop, List<MatchDay> days) {
        if (days == null || days.isEmpty()) return -1;
        int index = dayIndexAtProgress(progressFromTop, days);
        MatchDay day = days.get(index);
        if (!day.collapsed()) return -1;
        long prefix = 0;
        for (int i = 0; i < index; i++) {
            prefix += days.get(i).matches();
        }
        return prefix + Math.round(fractionInDay(progressFromTop, days) * Math.max(0, day.matches() - 1));
    }

    /**
     * How far into its own day {@code progressFromTop} sits, in {@code [0, 1]}.
     */
    public static double fractionInDay(double progressFromTop, List<MatchDay> days) {
        double fromOldest = Math.clamp(progressFromTop, 0, 1);
        if (days == null || days.isEmpty()) return fromOldest;
        int index = dayIndexAtProgress(fromOldest, days);
        if (index == days.size() - 1 && fromOldest >= 1) return 1;
        return Math.clamp(fromOldest * days.size() - index, 0, 1);
    }

    /**
     * The occupied day that {@code progressFromTop} falls on, or {@code null} when there are none.
     */
    public static MatchDay dayAtProgress(double progressFromTop, List<MatchDay> days) {
        if (days == null || days.isEmpty()) return null;
        return days.get(dayIndexAtProgress(progressFromTop, days));
    }

    static int dayIndex(LocalDate target, List<MatchDay> days) {
        int index = 0;
        for (int i = 0; i < days.size(); i++) {
            LocalDate day = days.get(i).date();
            if (day.equals(target)) return i;
            if (day.isBefore(target)) index = i;
            else break;
        }
        return index;
    }

    static double dayFraction(LocalDateTime time, MatchDay day, double collapsedFraction) {
        if (day.collapsed()) {
            return Double.isNaN(collapsedFraction) ? 0 : Math.clamp(collapsedFraction, 0, 1);
        }
        if (time == null) return 0;
        if (time.toLocalDate().isBefore(day.date())) return 0;
        if (time.toLocalDate().isAfter(day.date())) return 1;
        return linearProgress(time, day.oldest(), day.newest());
    }

    static LocalDateTime interpolate(LocalDateTime first, LocalDateTime last, double fraction) {
        if (first == null) return last;
        if (last == null || !last.isAfter(first)) return first;
        long millis = Math.round(Duration.between(first, last).toMillis() * Math.clamp(fraction, 0, 1));
        return first.plus(Duration.ofMillis(millis));
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

    private static int dayIndexAtProgress(double progressFromTop, List<MatchDay> days) {
        double scaled = Math.clamp(progressFromTop, 0, 1) * days.size();
        return Math.clamp((int) Math.floor(scaled), 0, days.size() - 1);
    }
}
