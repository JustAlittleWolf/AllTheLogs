package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.ChatLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uses one extra fetched context line that is not shown, so cluster edges know whether they can still grow.
 * Expand stays on the same calendar day; neighbouring chat logs on that day can still be filled from either side.
 */
public final class ContextPeeks {
    private ContextPeeks() {
    }

    /**
     * Drops lines that are only there to detect more content, and marks the visible edges that can expand.
     */
    public static List<DisplayRow> strip(List<DisplayRow> rows, int contextLines, boolean hasText,
                                         boolean oldestFirst) {
        if (!hasText || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : List.copyOf(rows);
        }
        Map<ChatLog, List<Integer>> matchLines = new HashMap<>();
        for (DisplayRow row : rows) {
            if (row.match()) {
                matchLines.computeIfAbsent(row.chatLog(), key -> new ArrayList<>()).add(row.lineIndex());
            }
        }
        List<DisplayRow> visible = new ArrayList<>();
        List<DisplayRow> peeks = new ArrayList<>();
        for (DisplayRow row : rows) {
            if (row.match()) {
                visible.add(row);
                continue;
            }
            int distance = minDistance(row.lineIndex(), matchLines.get(row.chatLog()));
            if (distance <= contextLines) {
                visible.add(row);
            } else if (distance == contextLines + 1) {
                peeks.add(row);
            }
        }
        for (DisplayRow peek : peeks) {
            LocalDate peekDay = peek.entry().timestamp().toLocalDate();
            for (int i = 0; i < visible.size(); i++) {
                DisplayRow row = visible.get(i);
                if (!row.sameLog(peek)) continue;
                if (!row.entry().timestamp().toLocalDate().equals(peekDay)) continue;
                if (row.lineIndex() == peek.lineIndex() + 1) {
                    visible.set(i, addFileExpand(row, true, false, oldestFirst));
                } else if (row.lineIndex() == peek.lineIndex() - 1) {
                    visible.set(i, addFileExpand(row, false, true, oldestFirst));
                }
            }
        }
        return List.copyOf(visible);
    }

    /**
     * Marks same-day, same-log holes in a date- or version-filtered page so those edges can expand.
     * Unfiltered pages do not call this: every stored line is already in the result, and a line-index
     * gap is missing file numbers, not hidden chat.
     */
    public static List<DisplayRow> markFileGaps(List<DisplayRow> rows, boolean oldestFirst) {
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
            if (Math.abs(current.lineIndex() - previous.lineIndex()) <= 1) continue;
            boolean laterInFile = current.lineIndex() > previous.lineIndex();
            out[i - 1] = addFileExpand(previous, !laterInFile, laterInFile, oldestFirst);
            out[i] = addFileExpand(current, laterInFile, !laterInFile, oldestFirst);
        }
        return List.of(out);
    }

    /**
     * Keeps an expand fetch on {@code anchor}'s calendar day, hides the extra probe line, and marks the new edge.
     */
    public static List<DisplayRow> forExpand(List<DisplayRow> fetched, DisplayRow anchor, boolean olderInFile,
                                             int extra, boolean oldestFirst) {
        if (fetched == null || fetched.isEmpty() || anchor == null) return List.of();
        LocalDate day = anchor.entry().timestamp().toLocalDate();
        int peekLine = anchor.lineIndex() + (olderInFile ? -(extra + 1) : extra + 1);
        boolean sawPeek = false;
        List<DisplayRow> kept = new ArrayList<>();
        for (DisplayRow row : fetched) {
            if (!row.entry().timestamp().toLocalDate().equals(day)) continue;
            if (row.sameLog(anchor) && row.lineIndex() == peekLine) {
                sawPeek = true;
                continue;
            }
            kept.add(row);
        }
        if (!sawPeek) return List.copyOf(kept);
        int farLine = olderInFile ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        for (DisplayRow row : kept) {
            if (!row.sameLog(anchor)) continue;
            farLine = olderInFile ? Math.min(farLine, row.lineIndex()) : Math.max(farLine, row.lineIndex());
        }
        if (farLine == Integer.MAX_VALUE || farLine == Integer.MIN_VALUE) return List.copyOf(kept);
        for (int i = 0; i < kept.size(); i++) {
            DisplayRow row = kept.get(i);
            if (row.sameLog(anchor) && row.lineIndex() == farLine) {
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

    private static int minDistance(int lineIndex, List<Integer> matches) {
        if (matches == null || matches.isEmpty()) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (int match : matches) {
            best = Math.min(best, Math.abs(lineIndex - match));
        }
        return best;
    }
}
