package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.util.List;

/**
 * Playtime view for this Minecraft run. The timeline and the weekday screen share filters and grouping.
 * Nothing is written to disk.
 */
public final class PlaytimeSession {
    private static PlaytimeFilter filter = PlaytimeFilter.all();
    private static PlaytimeBreakdown breakdown = PlaytimeBreakdown.VERSION;
    private static PlaytimeStyle style = PlaytimeStyle.BARS;
    private static PlaytimeGrain grain = PlaytimeGrain.AUTO;
    private static PlaytimeWindow window;
    private static List<PlaytimeSpan> spans = List.of();
    private static boolean loaded;

    private PlaytimeSession() {
    }

    public static PlaytimeFilter filter() {
        return filter;
    }

    public static void filter(PlaytimeFilter next) {
        filter = next == null ? PlaytimeFilter.all() : next;
    }

    public static PlaytimeBreakdown breakdown() {
        return breakdown;
    }

    public static void breakdown(PlaytimeBreakdown next) {
        breakdown = next == null ? PlaytimeBreakdown.VERSION : next;
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
        style = PlaytimeStyle.BARS;
        grain = PlaytimeGrain.AUTO;
        window = null;
        spans = List.of();
        loaded = false;
    }
}
