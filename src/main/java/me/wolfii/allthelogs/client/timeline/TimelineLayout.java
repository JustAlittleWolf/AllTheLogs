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
     * Pixel Y with oldest at the top. Matches chronological message order in the log browser.
     */
    public static int y(LocalDateTime time, LocalDateTime first, LocalDateTime last, int top, int height) {
        return yFromOldest(time, first, last, List.of(), top, height);
    }

    /**
     * Pixel Y with newest at the top and oldest at the bottom.
     */
    public static int yFromNewest(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest, int top, int height) {
        return yFromNewest(time, oldest, newest, List.of(), top, height);
    }

    /**
     * Pixel Y with newest at the top. When {@code days} lists the days that actually contain matches,
     * empty days between them are omitted and each listed day gets an equal share of the track.
     */
    public static int yFromNewest(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest,
                                  List<LocalDate> days, int top, int height) {
        double fromNewest = 1 - progress(time, oldest, newest, days);
        return top + (int) Math.round(fromNewest * Math.max(0, height - 1));
    }

    /**
     * Pixel Y with oldest at the top and newest at the bottom, matching chronological message order.
     */
    public static int yFromOldest(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest,
                                  List<LocalDate> days, int top, int height) {
        double fromOldest = progress(time, oldest, newest, days);
        return top + (int) Math.round(fromOldest * Math.max(0, height - 1));
    }

    public static LocalDateTime timeFromNewest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest) {
        return timeFromNewest(progressFromTop, oldest, newest, List.of());
    }

    public static LocalDateTime timeFromNewest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest,
                                               List<LocalDate> days) {
        return timeFromOldest(1 - Math.clamp(progressFromTop, 0, 1), oldest, newest, days);
    }

    public static LocalDateTime timeFromOldest(double progressFromTop, LocalDateTime oldest, LocalDateTime newest,
                                               List<LocalDate> days) {
        if (days != null && days.size() >= 2) {
            return timeFromCompressedDays(progressFromTop, days);
        }
        LocalDateTime first = earlier(oldest, newest);
        LocalDateTime last = later(oldest, newest);
        if (first == null || last == null) return first;
        double clamped = Math.clamp(progressFromTop, 0, 1);
        long millis = Math.round(Duration.between(first, last).toMillis() * clamped);
        return first.plus(Duration.ofMillis(millis));
    }

    public static double progress(LocalDateTime time, LocalDateTime oldest, LocalDateTime newest,
                                  List<LocalDate> days) {
        if (days != null && days.size() >= 2) {
            return compressedProgress(time, days);
        }
        return progress(time, oldest, newest);
    }

    /**
     * 0 at the start of the oldest listed day, 1 at the end of the newest. Each matched day occupies the same
     * fraction of the track, so empty days between hits do not take space.
     */
    public static double compressedProgress(LocalDateTime time, List<LocalDate> days) {
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

    static LocalDateTime timeFromCompressedDays(double progressFromTop, List<LocalDate> days) {
        double fromOldest = Math.clamp(progressFromTop, 0, 1);
        double scaled = fromOldest * days.size();
        int index = Math.min(days.size() - 1, (int) Math.floor(scaled));
        if (index < 0) index = 0;
        double fraction = Math.clamp(scaled - index, 0, 1);
        if (index == days.size() - 1 && fromOldest >= 1) {
            fraction = 1;
        }
        LocalDate day = days.get(index);
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        long millis = Math.round(Duration.between(start, end).toMillis() * fraction);
        return start.plus(Duration.ofMillis(millis));
    }

    /**
     * Small Immich-style scrubber thumb. Taller when few occupied days are in the query, shorter when many
     * are. Independent of scroll position and of the loaded page.
     */
    public static int thumbHeightForDays(int trackHeight, int uniqueDates) {
        if (trackHeight <= 0) return 0;
        int min = Math.min(trackHeight, 16);
        int max = Math.min(trackHeight, Math.max(min, trackHeight / 5));
        int few = Math.min(max, Math.max(min, (int) Math.round(trackHeight * 0.16)));
        int many = min;
        int dates = Math.max(0, uniqueDates);
        if (dates <= 1) return few;
        if (dates >= 30) return many;
        double t = (dates - 1) / 29.0;
        return (int) Math.round(few + (many - few) * t);
    }

    /**
     * Thumb height on a track of {@code trackHeight} for a viewport of {@code viewHeight} in content of
     * {@code contentHeight}. Zero when everything fits, so the thumb can be hidden.
     */
    public static int thumbHeight(int trackHeight, int contentHeight, int viewHeight, int minThumb) {
        if (trackHeight <= 0 || viewHeight <= 0 || contentHeight <= viewHeight) return 0;
        int sized = (int) Math.round((double) viewHeight / contentHeight * trackHeight);
        return Math.clamp(sized, Math.max(1, minThumb), trackHeight);
    }

    /**
     * Scroll offset that puts {@code rowTop} at the top of the view, clamped so the last content can sit
     * on the bottom edge instead of leaving a gap.
     */
    public static double scrollToRow(int rowTop, int contentHeight, int viewHeight) {
        double max = Math.max(0, contentHeight - viewHeight);
        if (rowTop < 0) return 0;
        if (rowTop > max) return max;
        return rowTop;
    }

    /**
     * Thumb top offset from the track origin. {@code 0} when scrolled to the start, {@code trackHeight - thumbHeight}
     * when scrolled so the last content sits at the bottom of the view.
     */
    public static int thumbOffset(int trackHeight, int contentHeight, int viewHeight, double scrollY, int thumbHeight) {
        double maxScroll = Math.max(0, contentHeight - viewHeight);
        if (maxScroll <= 0) return 0;
        return thumbOffset(trackHeight, scrollY / maxScroll, thumbHeight);
    }

    /**
     * Thumb top for a 0–1 timeline progress. Oldest is 0 at the top.
     */
    public static int thumbOffset(int trackHeight, double progress, int thumbHeight) {
        if (thumbHeight <= 0 || thumbHeight >= trackHeight) return 0;
        double t = Math.clamp(progress, 0, 1);
        return (int) Math.round(t * (trackHeight - thumbHeight));
    }

    /**
     * Inverse of {@link #thumbOffset(int, double, int)}.
     */
    public static double progressFromThumb(int thumbTop, int trackHeight, int thumbHeight) {
        if (thumbHeight <= 0 || thumbHeight >= trackHeight) return 0;
        int travel = trackHeight - thumbHeight;
        if (travel <= 0) return 0;
        return Math.clamp(thumbTop / (double) travel, 0, 1);
    }

    /**
     * Distance from the thumb top to the pointer. Clicks on the thumb keep that grip; clicks on the track
     * grab the centre so the thumb does not jump.
     */
    public static double thumbGrabOffset(double localY, int thumbTop, int thumbHeight, int trackHeight) {
        if (thumbHeight <= 0 || thumbHeight >= trackHeight) return 0;
        if (localY >= thumbTop && localY < thumbTop + thumbHeight) {
            return localY - thumbTop;
        }
        return thumbHeight / 2.0;
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
     * Labels for occupied match days. A handful of days get a tick each; otherwise occupied months or years.
     * Oldest-at-top placement is done by {@link #spacedTicks}.
     */
    public static List<DateTick> ticks(List<LocalDate> days) {
        if (days == null || days.isEmpty()) return List.of();
        boolean multiYear = days.getFirst().getYear() != days.getLast().getYear();
        if (days.size() <= 8) {
            DateTimeFormatter format = multiYear
                ? DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US)
                : DAY;
            List<DateTick> ticks = new ArrayList<>(days.size());
            for (LocalDate day : days) {
                ticks.add(new DateTick(day.atStartOfDay(), day.format(format)));
            }
            return List.copyOf(ticks);
        }
        List<YearMonth> months = monthsOf(days);
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

    static List<YearMonth> monthsOf(List<LocalDate> days) {
        if (days == null || days.isEmpty()) return List.of();
        List<YearMonth> months = new ArrayList<>();
        YearMonth previous = null;
        for (LocalDate day : days) {
            YearMonth month = YearMonth.from(day);
            if (!month.equals(previous)) {
                months.add(month);
                previous = month;
            }
        }
        return months;
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
     * Date labels placed along an oldest-at-top track, spaced at least {@code minGapPx} apart so they stay readable.
     */
    public static List<DateTick> spacedTicks(LocalDateTime oldest, LocalDateTime newest, int height, int minGapPx) {
        return spacedTicks(oldest, newest, List.of(), height, minGapPx);
    }

    public static List<DateTick> spacedTicks(LocalDateTime oldest, LocalDateTime newest, List<LocalDate> days,
                                             int height, int minGapPx) {
        List<DateTick> raw = days != null && days.size() >= 2 ? ticks(days) : ticks(oldest, newest);
        if (raw.isEmpty() || height <= 0 || minGapPx <= 0) return raw;
        List<DateTick> ordered = new ArrayList<>(raw);
        ordered.sort((a, b) -> Integer.compare(
            yFromOldest(a.at(), oldest, newest, days, 0, height),
            yFromOldest(b.at(), oldest, newest, days, 0, height)));
        List<DateTick> kept = new ArrayList<>();
        int lastY = Integer.MIN_VALUE / 2;
        for (DateTick tick : ordered) {
            int y = yFromOldest(tick.at(), oldest, newest, days, 0, height);
            if (kept.isEmpty() || Math.abs(y - lastY) >= minGapPx) {
                kept.add(tick);
                lastY = y;
            }
        }
        DateTick last = ordered.getLast();
        int lastTickY = yFromOldest(last.at(), oldest, newest, days, 0, height);
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
