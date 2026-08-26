package me.wolfii.allthelogs.view;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Sliding window of displayed log rows. Replacing the buffered page adjusts the scroll offset so the same
 * row stays on screen when old rows are dropped and new ones are loaded.
 */
public final class ResultWindow {
    private List<DisplayRow> rows = List.of();
    private boolean hasBefore;
    private boolean hasAfter;

    public static int indexOf(List<DisplayRow> rows, DisplayRow.RowKey key) {
        if (key == null) return -1;
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).key(), key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Newest-first queries return rows in reverse chronological order. After fetching the previous page with the
     * opposite sort, reverse that page so it can be prepended.
     */
    public static List<DisplayRow> reversed(List<DisplayRow> rows) {
        List<DisplayRow> copy = new ArrayList<>(rows);
        java.util.Collections.reverse(copy);
        return List.copyOf(copy);
    }

    /**
     * Concatenates {@code older} then {@code newer}, dropping duplicate row keys.
     */
    public static List<DisplayRow> mergeUnique(List<DisplayRow> older, List<DisplayRow> newer) {
        List<DisplayRow> merged = new ArrayList<>(older.size() + newer.size());
        Set<DisplayRow.RowKey> seen = new HashSet<>();
        for (DisplayRow row : older) {
            if (seen.add(row.key())) merged.add(row);
        }
        for (DisplayRow row : newer) {
            if (seen.add(row.key())) merged.add(row);
        }
        return List.copyOf(merged);
    }

    /**
     * Keeps at most {@code matchLimit} matches, covering the visible row range whenever possible so paging does
     * not jump the viewport.
     */
    public static List<DisplayRow> trimToMatchLimit(List<DisplayRow> rows, int matchLimit,
                                                    int firstVisibleRow, int lastVisibleRow) {
        if (rows.isEmpty() || matchLimit < 1) return List.copyOf(rows);
        List<Integer> matchIndexes = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).match()) matchIndexes.add(i);
        }
        if (matchIndexes.size() <= matchLimit) return List.copyOf(rows);

        int visibleStart = Math.clamp(firstVisibleRow, 0, rows.size() - 1);
        int visibleEnd = Math.clamp(lastVisibleRow, 0, rows.size() - 1);
        int firstVisibleMatch = 0;
        int lastVisibleMatch = matchIndexes.size() - 1;
        for (int i = 0; i < matchIndexes.size(); i++) {
            if (matchIndexes.get(i) >= visibleStart) {
                firstVisibleMatch = i;
                break;
            }
        }
        for (int i = matchIndexes.size() - 1; i >= 0; i--) {
            if (matchIndexes.get(i) <= visibleEnd) {
                lastVisibleMatch = i;
                break;
            }
        }
        int visibleCount = Math.max(1, lastVisibleMatch - firstVisibleMatch + 1);
        int extra = matchLimit - visibleCount;
        int startMatch = firstVisibleMatch;
        int endMatch = lastVisibleMatch;
        if (extra > 0) {
            int before = extra - extra / 2;
            startMatch = Math.max(0, firstVisibleMatch - before);
            endMatch = Math.min(matchIndexes.size() - 1, startMatch + matchLimit - 1);
            startMatch = Math.max(0, endMatch - matchLimit + 1);
        }

        int fromRow = matchIndexes.get(startMatch);
        int toRow = matchIndexes.get(endMatch);
        while (fromRow > 0 && !rows.get(fromRow - 1).match()) fromRow--;
        while (toRow + 1 < rows.size() && !rows.get(toRow + 1).match()) toRow++;
        return List.copyOf(rows.subList(fromRow, toRow + 1));
    }

    public List<DisplayRow> rows() {
        return rows;
    }

    public boolean hasBefore() {
        return hasBefore;
    }

    public boolean hasAfter() {
        return hasAfter;
    }

    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        this.rows = List.copyOf(rows);
        this.hasBefore = hasBefore;
        this.hasAfter = hasAfter;
    }

    /**
     * Replaces the buffer with {@code next} and returns the scroll offset that keeps {@code anchor} at the same
     * pixel position, assuming a fixed {@code rowHeight}.
     */
    public double replaceKeepingAnchor(List<DisplayRow> next, boolean hasBefore, boolean hasAfter,
                                       DisplayRow.RowKey anchor, double scrollY, int rowHeight) {
        int oldIndex = indexOf(rows, anchor);
        int newIndex = indexOf(next, anchor);
        this.rows = List.copyOf(next);
        this.hasBefore = hasBefore;
        this.hasAfter = hasAfter;
        if (oldIndex < 0 || newIndex < 0) {
            return scrollY;
        }
        double screenY = oldIndex * (double) rowHeight - scrollY;
        return newIndex * (double) rowHeight - screenY;
    }

    public DisplayRow.RowKey keyAtPixel(double scrollY, int rowHeight) {
        if (rows.isEmpty() || rowHeight <= 0) return null;
        int index = (int) Math.floor(Math.max(0, scrollY) / rowHeight);
        if (index >= rows.size()) index = rows.size() - 1;
        return rows.get(index).key();
    }

    public int contentHeight(int rowHeight) {
        return rows.size() * rowHeight;
    }

    public LocalDateTime firstMatchTime() {
        for (DisplayRow row : rows) {
            if (row.match()) return row.entry().timestamp();
        }
        return rows.isEmpty() ? null : rows.getFirst().entry().timestamp();
    }

    public LocalDateTime lastMatchTime() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).match()) return rows.get(i).entry().timestamp();
        }
        return rows.isEmpty() ? null : rows.getLast().entry().timestamp();
    }

    public int matchCount() {
        int count = 0;
        for (DisplayRow row : rows) {
            if (row.match()) count++;
        }
        return count;
    }
}
