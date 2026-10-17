package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Playtime view for this Minecraft run. The timeline and the weekday screen share filters and grouping.
 * Nothing is written to disk.
 */
public final class PlaytimeSession {
    private static PlaytimeFilter filter = PlaytimeFilter.all();
    private static PlaytimeBreakdown breakdown = PlaytimeBreakdown.VERSION;
    private static boolean majorVersions;
    private static PlaytimeStyle style = PlaytimeStyle.BARS;
    private static PlaytimeGrain grain = PlaytimeGrain.AUTO;
    private static PlaytimeWindow window;
    private static LocalDateTime weekdayFrom;
    private static LocalDateTime weekdayUntil;
    private static int heatSlotMinutes = PlaytimeWeekday.SLOT_MINUTES[0];
    private static List<PlaytimeSpan> spans = List.of();
    private static boolean loaded;

    private PlaytimeSession() {
    }

    public static PlaytimeFilter filter() {
        return filter;
    }

    /**
     * Changing the filter zooms the timeline all the way out, so the whole filtered history is visible.
     */
    public static void filter(PlaytimeFilter next) {
        PlaytimeFilter changed = next == null ? PlaytimeFilter.all() : next;
        if (!changed.equals(filter)) window = null;
        filter = changed;
    }

    public static PlaytimeBreakdown breakdown() {
        return breakdown;
    }

    public static void breakdown(PlaytimeBreakdown next) {
        breakdown = next == null ? PlaytimeBreakdown.VERSION : next;
    }

    /**
     * Whether version groupings keep only the first two components, so {@code 1.8.9} is shown as {@code 1.8}.
     */
    public static boolean majorVersions() {
        return majorVersions;
    }

    public static void majorVersions(boolean next) {
        majorVersions = next;
    }

    public static PlaytimeStyle style() {
        return style;
    }

    public static void style(PlaytimeStyle next) {
        style = next == null ? PlaytimeStyle.BARS : next;
    }

    public static PlaytimeGrain grain() {
        return grain;
    }

    public static void grain(PlaytimeGrain next) {
        grain = next == null ? PlaytimeGrain.AUTO : next;
    }

    public static PlaytimeWindow window() {
        return window;
    }

    public static void window(PlaytimeWindow next) {
        window = next;
    }

    /**
     * Inclusive start of the period the weekday screen covers, or null for the start of history.
     */
    public static LocalDateTime weekdayFrom() {
        return weekdayFrom;
    }

    public static void weekdayFrom(LocalDateTime next) {
        weekdayFrom = next;
    }

    /**
     * Exclusive end of the period the weekday screen covers, or null for no end.
     */
    public static LocalDateTime weekdayUntil() {
        return weekdayUntil;
    }

    public static void weekdayUntil(LocalDateTime next) {
        weekdayUntil = next;
    }

    /**
     * Slot length of the weekday heat map in minutes, one of {@link PlaytimeWeekday#SLOT_MINUTES}.
     */
    public static int heatSlotMinutes() {
        return heatSlotMinutes;
    }

    public static void heatSlotMinutes(int minutes) {
        heatSlotMinutes = minutes;
    }

    public static List<PlaytimeSpan> spans() {
        return spans;
    }

    public static boolean loaded() {
        return loaded;
    }

    public static void spans(List<PlaytimeSpan> next) {
        spans = next == null ? List.of() : List.copyOf(next);
        loaded = true;
    }

    static void clear() {
        filter = PlaytimeFilter.all();
        breakdown = PlaytimeBreakdown.VERSION;
        majorVersions = false;
        style = PlaytimeStyle.BARS;
        grain = PlaytimeGrain.AUTO;
        window = null;
        weekdayFrom = null;
        weekdayUntil = null;
        heatSlotMinutes = PlaytimeWeekday.SLOT_MINUTES[0];
        spans = List.of();
        loaded = false;
    }
}
