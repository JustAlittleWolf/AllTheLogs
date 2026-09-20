package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.ChatLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses one extra fetched context line that is not shown, so cluster edges know whether they can still grow.
 * Expand stays on the same calendar day; neighbouring chat logs on that day can still be filled from either side.
 * Distances count stored chat that passed the filter bar, not raw file line numbers.
 */
public final class ContextPeeks {
    private ContextPeeks() {
    }

    /**
     * Marks expand carets for a result page. Text search peeks around hits. Without a search term every
     * filter-bar match is already in the page, so there is nothing to expand.
     */
    public static List<DisplayRow> forSearchPage(List<DisplayRow> rows, boolean hasText, int contextLines,
                                                 boolean oldestFirst) {
        if (rows == null) return List.of();
        if (hasText) {
            return strip(rows, contextLines, true, oldestFirst);
        }
        return List.copyOf(rows);
    }

    /**
     * Drops lines that are only there to detect more content, and marks the visible edges that can expand.
     * Distance is the number of fetched same-log rows to the nearest hit, so other-server neighbours that
     * were skipped in SQL do not consume the context budget.
     */
    public static List<DisplayRow> strip(List<DisplayRow> rows, int contextLines, boolean hasText,
                                         boolean oldestFirst) {
        if (!hasText || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : List.copyOf(rows);
        }
        Map<DisplayRow, Integer> rankFromHit = rankFromNearestHit(rows);
        List<DisplayRow> visible = new ArrayList<>();
        List<DisplayRow> peeks = new ArrayList<>();
        for (DisplayRow row : rows) {
            int distance = rankFromHit.getOrDefault(row, Integer.MAX_VALUE);
            if (row.match() || distance <= contextLines) {
                visible.add(row);
            } else if (distance == contextLines + 1) {
                peeks.add(row);
            }
        }
        for (DisplayRow peek : peeks) {
            LocalDate peekDay = peek.entry().timestamp().toLocalDate();
            DisplayRow edge = null;
            int best = Integer.MAX_VALUE;
            boolean moreBefore = false;
            for (DisplayRow row : visible) {
                if (!row.sameLog(peek)) continue;
                if (!row.entry().timestamp().toLocalDate().equals(peekDay)) continue;
                int gap = row.lineIndex() - peek.lineIndex();
                int abs = Math.abs(gap);
                if (abs == 0 || abs >= best) continue;
                best = abs;
                edge = row;
                moreBefore = gap > 0;
            }
            if (edge == null) continue;
            int index = visible.indexOf(edge);
            visible.set(index, addFileExpand(edge, moreBefore, !moreBefore, oldestFirst));
        }
        return List.copyOf(visible);
    }

    /**
     * Keeps an expand fetch on {@code anchor}'s calendar day, hides the extra probe line, and marks the new edge.
     * {@code extra} is a count of filter-matching lines, not file indices.
     */
    public static List<DisplayRow> forExpand(List<DisplayRow> fetched, DisplayRow anchor, boolean olderInFile,
                                             int extra, boolean oldestFirst) {
        if (fetched == null || fetched.isEmpty() || anchor == null) return List.of();
        LocalDate day = anchor.entry().timestamp().toLocalDate();
        List<DisplayRow> kept = new ArrayList<>();
        List<DisplayRow> toward = new ArrayList<>();
        for (DisplayRow row : fetched) {
            if (!row.entry().timestamp().toLocalDate().equals(day)) continue;
            boolean onSide = row.sameLog(anchor)
                && (olderInFile ? row.lineIndex() < anchor.lineIndex() : row.lineIndex() > anchor.lineIndex());
            if (onSide) {
                toward.add(row);
            } else {
                kept.add(row);
            }
        }
        toward.sort(Comparator.comparingInt(DisplayRow::lineIndex));
        DisplayRow peek = null;
        if (toward.size() > extra) {
            peek = olderInFile ? toward.removeFirst() : toward.removeLast();
        }
        kept.addAll(toward);
        if (peek == null || toward.isEmpty()) return List.copyOf(kept);
        DisplayRow far = olderInFile ? toward.getFirst() : toward.getLast();
        for (int i = 0; i < kept.size(); i++) {
            DisplayRow row = kept.get(i);
            if (row.sameLog(anchor) && row.lineIndex() == far.lineIndex()) {
                kept.set(i, addFileExpand(row, olderInFile, !olderInFile, oldestFirst));
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Merges an expand fetch into the open page and clears the caret that was just used on the anchor.
     */
    public static List<DisplayRow> mergeAfterExpand(List<DisplayRow> existing, List<DisplayRow> expanded,
                                                    DisplayRow anchor, boolean olderInFile, ChatQuery.Sort sort) {
        boolean oldestFirst = sort == ChatQuery.Sort.ASCENDING;
        List<DisplayRow> cleared = new ArrayList<>(existing.size());
        for (DisplayRow row : existing) {
            if (anchor != null && row.key().equals(anchor.key())) {
                cleared.add(clearExpandedSide(row, olderInFile, oldestFirst));
            } else {
                cleared.add(row);
            }
        }
        return clearClosedGaps(DisplayRows.mergeSorted(cleared, expanded, sort));
    }

    /**
     * After an expand fetch, neighbouring clusters that now touch must lose the facing carets: expanding
     * up re-checks whether the cluster above can still grow down, and expanding down re-checks the cluster
     * below. Adjacent same-log, same-day lines have no remaining gap.
     */
    static List<DisplayRow> clearClosedGaps(List<DisplayRow> rows) {
        if (rows == null || rows.size() < 2) {
            return rows == null ? List.of() : List.copyOf(rows);
        }
        DisplayRow[] out = rows.toArray(DisplayRow[]::new);
        for (int i = 1; i < out.length; i++) {
            DisplayRow previous = out[i - 1];
            DisplayRow current = out[i];
            if (!previous.sameLog(current)) continue;
            if (!previous.entry().timestamp().toLocalDate().equals(current.entry().timestamp().toLocalDate())) {
                continue;
            }
            if (Math.abs(current.lineIndex() - previous.lineIndex()) > 1) continue;
            out[i - 1] = previous.withExpand(previous.expandUp(), false);
            out[i] = current.withExpand(false, current.expandDown());
        }
        return List.of(out);
    }

    static DisplayRow addFileExpand(DisplayRow row, boolean moreBefore, boolean moreAfter, boolean oldestFirst) {
        boolean up = row.expandUp();
        boolean down = row.expandDown();
        if (oldestFirst) {
            if (moreBefore) up = true;
            if (moreAfter) down = true;
        } else {
            if (moreAfter) up = true;
            if (moreBefore) down = true;
        }
        return row.withExpand(up, down);
    }

    private static DisplayRow clearExpandedSide(DisplayRow row, boolean olderInFile, boolean oldestFirst) {
        boolean up = row.expandUp();
        boolean down = row.expandDown();
        if (oldestFirst) {
            if (olderInFile) up = false;
            else down = false;
        } else {
            if (olderInFile) down = false;
            else up = false;
        }
        return row.withExpand(up, down);
    }

    private static Map<DisplayRow, Integer> rankFromNearestHit(List<DisplayRow> rows) {
        Map<ChatLog, List<DisplayRow>> byLog = new LinkedHashMap<>();
        for (DisplayRow row : rows) {
            byLog.computeIfAbsent(row.chatLog(), key -> new ArrayList<>()).add(row);
        }
        Map<DisplayRow, Integer> rank = new IdentityHashMap<>();
        for (List<DisplayRow> group : byLog.values()) {
            group.sort(Comparator.comparingInt(DisplayRow::lineIndex));
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < group.size(); i++) {
                if (group.get(i).match()) hits.add(i);
            }
            for (int i = 0; i < group.size(); i++) {
                rank.put(group.get(i), minDistance(i, hits));
            }
        }
        return rank;
    }

    private static int minDistance(int index, List<Integer> hits) {
        if (hits == null || hits.isEmpty()) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (int hit : hits) {
            best = Math.min(best, Math.abs(index - hit));
        }
        return best;
    }
}
