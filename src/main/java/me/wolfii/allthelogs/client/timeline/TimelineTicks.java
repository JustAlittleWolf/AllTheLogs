package me.wolfii.allthelogs.client.timeline;

import me.wolfii.allthelogs.data.MatchDay;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chooses the date labels drawn along the timeline track, and the hover label for the scrubber.
 * <p>
 * The label granularity follows how much the query spans: individual days for a handful of them, then months,
 * then years. {@link #spacedTicks} drops labels that would collide once they are placed on the track.
 */
public final class TimelineTicks {
    private static final DateTimeFormatter HOVER_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.US);
    private static final DateTimeFormatter HOVER_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY_AND_YEAR = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US);
    private static final DateTimeFormatter BARE_MONTH = DateTimeFormatter.ofPattern("MMM", Locale.US);
    private static final int MAX_DAY_TICKS = 8;
    private static final int MAX_MONTH_TICKS = 36;
    private static final int MAX_BARE_MONTH_TICKS = 4;

    private TimelineTicks() {
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
     * Date labels for an oldest-at-top track, spaced at least {@code minGapPx} apart so they stay readable.
     * When {@code days} lists two or more occupied match days, labels come from those days and are placed on
     * the equal-share-per-day scale; otherwise they are stepped through wall-clock time between the bounds.
     */
    public static List<DateTick> spacedTicks(LocalDateTime oldest, LocalDateTime newest, List<MatchDay> days,
                                             int height, int minGapPx) {
        List<LocalDate> dates = datesOf(days);
        boolean byDay = dates.size() >= 2;
        List<DateTick> raw = byDay ? occupiedDayTicks(dates) : steppedTicks(oldest, newest);
        if (raw.isEmpty() || height <= 0 || minGapPx <= 0) return raw;

        List<DateTick> ordered = new ArrayList<>(raw);
        ordered.sort((a, b) -> Integer.compare(
            tickY(a, oldest, newest, dates, byDay, height),
            tickY(b, oldest, newest, dates, byDay, height)));
        List<DateTick> kept = new ArrayList<>();
        int lastY = Integer.MIN_VALUE / 2;
        for (DateTick tick : ordered) {
            int y = tickY(tick, oldest, newest, dates, byDay, height);
            if (kept.isEmpty() || Math.abs(y - lastY) >= minGapPx) {
                kept.add(tick);
                lastY = y;
            }
        }
        DateTick last = ordered.getLast();
        int lastTickY = tickY(last, oldest, newest, dates, byDay, height);
        if (!kept.getLast().equals(last) && Math.abs(lastTickY - lastY) >= minGapPx) {
            kept.add(last);
        }
        return List.copyOf(kept);
    }

    static List<LocalDate> datesOf(List<MatchDay> days) {
        if (days == null || days.isEmpty()) return List.of();
        List<LocalDate> dates = new ArrayList<>(days.size());
        for (MatchDay day : days) {
            dates.add(day.date());
        }
        return List.copyOf(dates);
    }

    /**
     * One label per occupied day while there are few of them, then one per occupied month, then one per year.
     */
    static List<DateTick> occupiedDayTicks(List<LocalDate> days) {
        if (days == null || days.isEmpty()) return List.of();
        boolean multiYear = days.getFirst().getYear() != days.getLast().getYear();
        if (days.size() <= MAX_DAY_TICKS) {
            return dayTicks(days, multiYear ? DAY_AND_YEAR : DAY);
        }
        List<YearMonth> months = monthsOf(days);
        if (months.size() > MAX_MONTH_TICKS) {
            return yearTicks(months);
        }
        return monthTicks(months, multiYear || months.size() > MAX_BARE_MONTH_TICKS ? MONTH : BARE_MONTH);
    }

    /**
     * Labels stepped through wall-clock time between two bounds: years for very wide spans, then months,
     * then a handful of days.
     */
    static List<DateTick> steppedTicks(LocalDateTime first, LocalDateTime last) {
        if (first == null || last == null) return List.of();
        LocalDateTime oldest = TimelineScale.earlier(first, last);
        LocalDate start = oldest.toLocalDate();
        LocalDate end = TimelineScale.later(first, last).toLocalDate();
        if (end.isBefore(start)) return List.of();
        long days = Duration.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).toDays();
        if (days > 400) {
            LocalDate firstYear = LocalDate.of(start.getYear(), 1, 1);
            return stepped(firstYear.isBefore(start) ? firstYear.plusYears(1) : firstYear, end,
                YEAR, cursor -> cursor.plusYears(1));
        }
        if (days > 45) {
            LocalDate firstMonth = LocalDate.of(start.getYear(), start.getMonth(), 1);
            return stepped(firstMonth.isBefore(start) ? firstMonth.plusMonths(1) : firstMonth, end,
                MONTH, cursor -> cursor.plusMonths(1));
        }
        if (days > 1) {
            long step = Math.max(1, days / 6);
            return stepped(start, end, DAY, cursor -> cursor.plusDays(step));
        }
        return List.of(new DateTick(oldest, oldest.format(DAY)));
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

    private static int tickY(DateTick tick, LocalDateTime oldest, LocalDateTime newest, List<LocalDate> dates,
                             boolean byDay, int height) {
        double progress = byDay
            ? TimelineScale.dayProgress(tick.at(), dates)
            : TimelineScale.linearProgress(tick.at(), oldest, newest);
        return TimelineScale.yAtProgress(progress, 0, height);
    }

    private static List<DateTick> dayTicks(List<LocalDate> days, DateTimeFormatter format) {
        List<DateTick> ticks = new ArrayList<>(days.size());
        for (LocalDate day : days) {
            ticks.add(new DateTick(day.atStartOfDay(), day.format(format)));
        }
        return List.copyOf(ticks);
    }

    private static List<DateTick> monthTicks(List<YearMonth> months, DateTimeFormatter format) {
        List<DateTick> ticks = new ArrayList<>(months.size());
        for (YearMonth month : months) {
            ticks.add(new DateTick(month.atDay(1).atStartOfDay(), month.format(format)));
        }
        return List.copyOf(ticks);
    }

    private static List<DateTick> yearTicks(List<YearMonth> months) {
        List<DateTick> ticks = new ArrayList<>();
        Integer previousYear = null;
        for (YearMonth month : months) {
            if (previousYear != null && previousYear == month.getYear()) continue;
            ticks.add(new DateTick(month.atDay(1).atStartOfDay(), month.format(YEAR)));
            previousYear = month.getYear();
        }
        return List.copyOf(ticks);
    }

    private static List<DateTick> stepped(LocalDate from, LocalDate to, DateTimeFormatter format,
                                          java.util.function.UnaryOperator<LocalDate> next) {
        List<DateTick> ticks = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = next.apply(cursor)) {
            ticks.add(new DateTick(cursor.atStartOfDay(), cursor.format(format)));
        }
        return List.copyOf(ticks);
    }

    /**
     * One date label on the track.
     *
     * @param at    the timestamp the label is placed at
     * @param label the text drawn next to the tick
     */
    public record DateTick(LocalDateTime at, String label) {
    }
}
