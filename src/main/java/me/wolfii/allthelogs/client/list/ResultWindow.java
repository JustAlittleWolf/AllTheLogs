package me.wolfii.allthelogs.client.list;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The page of rows currently on screen, plus whether more exist on either side of it.
 * <p>
 * Only a window of the result set is ever loaded, so paging replaces the whole buffer. Operations on a page of
 * rows live in {@link DisplayRows}; this type just holds the one that is displayed.
 */
public final class ResultWindow {
    private List<DisplayRow> rows = List.of();
    private boolean hasBefore;
    private boolean hasAfter;

    /**
     * Scroll offset that keeps an anchored row at the same screen position after the buffer is replaced,
     * including when bottom-padding ({@code origin}) appears or disappears as the page grows.
     */
    public static double keepAnchor(int oldIndex, int newIndex, double oldY, double newY, double scrollY,
                                    double oldOrigin, double newOrigin) {
        if (oldIndex < 0 || newIndex < 0) return scrollY;
        return newOrigin + newY - (oldOrigin + oldY - scrollY);
    }

    public List<DisplayRow> rows() {
        return rows;
    }

    /** Whether matches exist before the first buffered row. */
    public boolean hasBefore() {
        return hasBefore;
    }

    /** Whether matches exist after the last buffered row. */
    public boolean hasAfter() {
        return hasAfter;
    }

    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        this.rows = List.copyOf(rows);
        this.hasBefore = hasBefore;
        this.hasAfter = hasAfter;
    }

    public DisplayRow.RowKey keyAt(int index) {
        if (index < 0 || index >= rows.size()) return null;
        return rows.get(index).key();
    }

    public LocalDateTime firstMatchTime() {
        return DisplayRows.firstMatchTime(rows);
    }

    public LocalDateTime lastMatchTime() {
        return DisplayRows.lastMatchTime(rows);
    }

    public int matchCount() {
        return DisplayRows.matchCount(rows);
    }

    /**
     * Whether {@code time} falls between the earliest and latest timestamps currently buffered.
     */
    public boolean coversTime(LocalDateTime time) {
        if (rows.isEmpty() || time == null) return false;
        LocalDateTime min = rows.getFirst().entry().timestamp();
        LocalDateTime max = min;
        for (DisplayRow row : rows) {
            LocalDateTime at = row.entry().timestamp();
            if (at.isBefore(min)) min = at;
            if (at.isAfter(max)) max = at;
        }
        return !time.isBefore(min) && !time.isAfter(max);
    }

    /**
     * Whether the buffered page already has a row on {@code time}'s calendar day. Used to scroll locally
     * instead of treating empty gaps between the first and last timestamps as loaded content.
     */
    public boolean showsDate(LocalDateTime time) {
        if (!coversTime(time) || time == null) return false;
        int index = nearestIndex(time);
        if (index < 0) return false;
        return rows.get(index).entry().timestamp().toLocalDate().equals(time.toLocalDate());
    }

    /**
     * Index of the row whose timestamp is closest to {@code time}, or {@code -1} when the window is empty.
     */
    public int nearestIndex(LocalDateTime time) {
        if (rows.isEmpty() || time == null) return -1;
        int best = 0;
        long bestDelta = Long.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            long delta = Math.abs(Duration.between(rows.get(i).entry().timestamp(), time).toMillis());
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }
}
