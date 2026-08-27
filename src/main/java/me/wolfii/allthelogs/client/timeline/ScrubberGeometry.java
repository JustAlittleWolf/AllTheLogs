package me.wolfii.allthelogs.client.timeline;

/**
 * Pixel geometry of the timeline scrubber: how tall its thumb is, where the thumb sits for a given progress,
 * and which scroll offset a progress maps to. Nothing here knows about timestamps.
 */
public final class ScrubberGeometry {
    private static final int MIN_THUMB = 16;
    /** Fraction of the track the thumb takes when the query holds a single day. */
    private static final double SINGLE_DAY_THUMB = 0.16;
    /** Above this many occupied days the thumb has shrunk to {@link #MIN_THUMB}. */
    private static final int MANY_DAYS = 30;

    private ScrubberGeometry() {
    }

    /**
     * Small Immich-style scrubber thumb. Taller when few occupied days are in the query, shorter when many
     * are; independent of scroll position. {@code 0} when the loaded content already fits, so the draggable
     * thumb can be hidden.
     */
    public static int thumbHeightForDays(int trackHeight, int uniqueDates, int contentHeight, int viewHeight) {
        if (trackHeight <= 0 || (viewHeight > 0 && contentHeight <= viewHeight)) return 0;
        int min = Math.min(trackHeight, MIN_THUMB);
        int max = Math.min(trackHeight, Math.max(min, trackHeight / 5));
        int few = Math.min(max, Math.max(min, (int) Math.round(trackHeight * SINGLE_DAY_THUMB)));
        int dates = Math.max(0, uniqueDates);
        if (dates <= 1) return few;
        if (dates >= MANY_DAYS) return min;
        double towardsMany = (dates - 1) / (double) (MANY_DAYS - 1);
        return (int) Math.round(few + (min - few) * towardsMany);
    }

    /**
     * Timeline progress with the thumb pinned to the track ends when the viewport is at the start or end of
     * the loaded content.
     */
    public static double pinnedProgress(double progress, boolean atStart, boolean atEnd) {
        if (atEnd) return 1;
        if (atStart) return 0;
        return Math.clamp(progress, 0, 1);
    }

    /**
     * Thumb top for a 0–1 timeline progress. Oldest is 0 at the top.
     */
    public static int thumbOffset(int trackHeight, double progress, int thumbHeight) {
        if (thumbHeight <= 0 || thumbHeight >= trackHeight) return 0;
        return (int) Math.round(Math.clamp(progress, 0, 1) * (trackHeight - thumbHeight));
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

    /**
     * Scroll offset that walks through a day's loaded rows. {@code 0} puts the date header at the top of the
     * view; {@code 1} puts the last content of that day on the bottom edge when the day is taller than the view.
     */
    public static double scrollForDateFraction(int dateStartY, int dateEndY, int viewHeight, double fraction) {
        int travel = Math.max(0, dateEndY - dateStartY - Math.max(0, viewHeight));
        return dateStartY + Math.clamp(fraction, 0, 1) * travel;
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
}
