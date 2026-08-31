package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.api.ChatQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Operations on a page of display rows: counting hits, finding rows again after a reload, and stitching a
 * newly fetched page onto the buffered one.
 * <p>
 * Rows are identified by {@link DisplayRow.RowKey} rather than by position, because consecutive pages overlap
 * and the same row can arrive twice.
 */
public final class DisplayRows {
    private DisplayRows() {
    }

    public static int matchCount(List<DisplayRow> rows) {
        int count = 0;
        for (DisplayRow row : rows) {
            if (row.match()) count++;
        }
        return count;
    }

    /**
     * Search hits in {@code rows} that fall on {@code date}. Used to tell a fully loaded day from a
     * scrubber preview that only fetched a slice of it.
     */
    public static int matchCountOnDate(List<DisplayRow> rows, LocalDate date) {
        if (rows == null || date == null) return 0;
        int count = 0;
        for (DisplayRow row : rows) {
            if (row.match() && date.equals(row.entry().timestamp().toLocalDate())) count++;
        }
        return count;
    }

    /**
     * How many search hits in {@code rows} share {@code time}. Used as {@link ChatQuery#withSkip(long)}
     * when paging from a timestamp that several matches occupy.
     */
    public static int matchCountAt(List<DisplayRow> rows, LocalDateTime time) {
        if (rows == null || time == null) return 0;
        int count = 0;
        for (DisplayRow row : rows) {
            if (row.match() && time.equals(row.entry().timestamp())) count++;
        }
        return count;
    }

    /**
     * Position of {@code key} in {@code rows}, or {@code -1} when it is not loaded.
     */
    public static int indexOf(List<DisplayRow> rows, DisplayRow.RowKey key) {
        if (key == null) return -1;
        for (int i = 0; i < rows.size(); i++) {
            if (Objects.equals(rows.get(i).key(), key)) {
                return i;
            }
        }
        return -1;
    }

    public static Set<DisplayRow.RowKey> keysOf(List<DisplayRow> rows) {
        Set<DisplayRow.RowKey> keys = HashSet.newHashSet(rows.size());
        for (DisplayRow row : rows) {
            keys.add(row.key());
        }
        return keys;
    }

    /**
     * How many of {@code rows} are not already in {@code existing}.
     */
    public static int countNewKeys(List<DisplayRow> rows, Set<DisplayRow.RowKey> existing) {
        int count = 0;
        for (DisplayRow row : rows) {
            if (!existing.contains(row.key())) count++;
        }
        return count;
    }

    /**
     * Timestamp of the first hit, falling back to the first row when the page holds only context lines.
     */
    public static LocalDateTime firstMatchTime(List<DisplayRow> rows) {
        for (DisplayRow row : rows) {
            if (row.match()) return row.entry().timestamp();
        }
        return rows.isEmpty() ? null : rows.getFirst().entry().timestamp();
    }

    /**
     * Timestamp of the last hit, falling back to the last row when the page holds only context lines.
     */
    public static LocalDateTime lastMatchTime(List<DisplayRow> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).match()) return rows.get(i).entry().timestamp();
        }
        return rows.isEmpty() ? null : rows.getLast().entry().timestamp();
    }

    /**
     * After fetching the previous page with the opposite sort, reverse that page so it can be prepended in list
     * order.
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
     * Merges {@code extra} into {@code existing} without duplicates, then orders by timestamp, then line index,
     * in {@code sort} order.
     */
    public static List<DisplayRow> mergeSorted(List<DisplayRow> existing, List<DisplayRow> extra,
                                               ChatQuery.Sort sort) {
        List<DisplayRow> merged = new ArrayList<>(mergeUnique(existing, extra));
        Comparator<DisplayRow> order = Comparator
            .comparing((DisplayRow row) -> row.entry().timestamp())
            .thenComparing(row -> String.valueOf(row.chatLog().source()))
            .thenComparingInt(DisplayRow::lineIndex);
        if (sort == ChatQuery.Sort.DESCENDING) {
            order = order.reversed();
        }
        merged.sort(order);
        return List.copyOf(merged);
    }

    /**
     * Keeps at most {@code matchLimit} matches, covering the visible row range whenever possible so paging does
     * not jump the viewport. Context lines around the kept matches are kept too.
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
     * Whether {@code window} dropped rows from the start of {@code merged}, which means more remain there.
     */
    public static boolean trimmedHead(List<DisplayRow> merged, List<DisplayRow> window) {
        return droppedEdge(merged, window, true);
    }

    /**
     * Whether {@code window} dropped rows from the end of {@code merged}, which means more remain there.
     */
    public static boolean trimmedTail(List<DisplayRow> merged, List<DisplayRow> window) {
        return droppedEdge(merged, window, false);
    }

    private static boolean droppedEdge(List<DisplayRow> merged, List<DisplayRow> window, boolean head) {
        if (merged == null || window == null || merged.isEmpty() || window.isEmpty()) return false;
        DisplayRow.RowKey mergedKey = head ? merged.getFirst().key() : merged.getLast().key();
        DisplayRow.RowKey windowKey = head ? window.getFirst().key() : window.getLast().key();
        return !mergedKey.equals(windowKey);
    }
}
