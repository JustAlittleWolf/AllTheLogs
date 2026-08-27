package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatQuery;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sliding window of displayed log rows. Replacing the buffered page adjusts the scroll offset so the same
 * row stays on screen when old rows are dropped and new ones are loaded.
 */
public final class ResultWindow {
    private List<DisplayRow> rows = List.of();
    private boolean hasBefore;
    private boolean hasAfter;

    public static int matchCount(List<DisplayRow> rows) {
        int count = 0;
        for (DisplayRow row : rows) {
            if (row.match()) count++;
        }
        return count;
    }

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
        return List.copyOf(rows).reversed();
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

    /**
     * Merges {@code extra} into {@code existing} without duplicates, then orders by timestamp, file, and line
     * in {@code sort} order.
     */
    public static List<DisplayRow> mergeSorted(List<DisplayRow> existing, List<DisplayRow> extra, ChatQuery.Sort sort) {
        List<DisplayRow> merged = new ArrayList<>(mergeUnique(existing, extra));
        Comparator<DisplayRow> order = Comparator
            .comparing((DisplayRow row) -> row.entry().timestamp())
            .thenComparingInt(DisplayRow::lineIndex)
            .thenComparing(row -> String.valueOf(row.chatLog().source()));
        if (sort == ChatQuery.Sort.DESCENDING) {
            order = order.reversed();
        }
        merged.sort(order);
        return List.copyOf(merged);
    }

    /**
     * Scroll offset that keeps an anchored row at the same screen position after the buffer is replaced.
     */
    public static double keepAnchor(int oldIndex, int newIndex, double oldY, double newY, double scrollY) {
        if (oldIndex < 0 || newIndex < 0) return scrollY;
        return newY - (oldY - scrollY);
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

    public DisplayRow.RowKey keyAt(int index) {
        if (index < 0 || index >= rows.size()) return null;
        return rows.get(index).key();
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

    public int matchCount() {
        return matchCount(rows);
    }
}
