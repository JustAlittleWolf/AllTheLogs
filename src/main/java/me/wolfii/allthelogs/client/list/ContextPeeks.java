package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return forSearchPage(rows, hasText, contextLines, oldestFirst, List.of());
    }

    /**
     * Same as {@link #forSearchPage(List, boolean, int, boolean)}, treating {@code neighbors} as rows already
     * on the page. A probe that fills the hole between those rows and this page is shown instead of becoming
     * a caret. Scrolling and a search that keeps the viewport fetch each side alone, so the shared boundary
     * line would otherwise stay hidden.
     */
    public static List<DisplayRow> forSearchPage(List<DisplayRow> rows, boolean hasText, int contextLines,
                                                 boolean oldestFirst, List<DisplayRow> neighbors) {
        if (rows == null) return List.of();
        if (hasText) {
            return strip(rows, contextLines, true, oldestFirst, neighbors);
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
        return strip(rows, contextLines, hasText, oldestFirst, List.of());
    }

    static List<DisplayRow> strip(List<DisplayRow> rows, int contextLines, boolean hasText,
                                  boolean oldestFirst, List<DisplayRow> neighbors) {
        if (!hasText || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : List.copyOf(rows);
        }
        Map<DisplayRow, Integer> rankFromHit = rankFromNearestHit(rows);
        List<DisplayRow> within = new ArrayList<>();
        List<DisplayRow> probes = new ArrayList<>();
        for (DisplayRow row : rows) {
            int distance = rankFromHit.getOrDefault(row, Integer.MAX_VALUE);
            if (row.match() || distance <= contextLines) {
                within.add(row);
            } else if (distance == contextLines + 1) {
                probes.add(row);
            }
        }
        List<DisplayRow> gapVisible = new ArrayList<>(within);
        if (neighbors != null) {
            for (DisplayRow neighbor : neighbors) {
                if (neighbor != null) gapVisible.add(neighbor);
            }
        }
        Set<DisplayRow> covered = coveredGapProbes(gapVisible, probes);
        List<DisplayRow> visible = new ArrayList<>();
        List<DisplayRow> peeks = new ArrayList<>();
        for (DisplayRow row : rows) {
            int distance = rankFromHit.getOrDefault(row, Integer.MAX_VALUE);
            if (row.match() || distance <= contextLines || covered.contains(row)) {
                visible.add(row);
            } else if (distance == contextLines + 1) {
                peeks.add(row);
            }
        }
        for (DisplayRow peek : peeks) {
            markNearestEdges(visible, peek, oldestFirst);
        }
        return List.copyOf(visible);
    }

    /**
     * Every visible row at the closest distance gets the caret. A tie used to keep only the earlier row, so a
     * gap showed a down arrow and no up arrow. A second probe on the same row also has to update the row
     * already replaced by the first probe; matching the original instance misses it once its flags change.
     */
    private static void markNearestEdges(List<DisplayRow> visible, DisplayRow peek, boolean oldestFirst) {
        LocalDate peekDay = peek.entry().timestamp().toLocalDate();
        int best = Integer.MAX_VALUE;
        List<Integer> edges = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            DisplayRow row = visible.get(i);
            if (!row.sameLog(peek)) continue;
            if (!row.entry().timestamp().toLocalDate().equals(peekDay)) continue;
            int gap = row.lineIndex() - peek.lineIndex();
            int abs = Math.abs(gap);
            if (abs == 0 || abs > best) continue;
            if (abs < best) {
                best = abs;
                edges.clear();
            }
            edges.add(i);
        }
        for (int index : edges) {
            DisplayRow edge = visible.get(index);
            boolean moreBefore = edge.lineIndex() > peek.lineIndex();
            visible.set(index, addFileExpand(edge, moreBefore, !moreBefore, oldestFirst));
        }
    }

    /**
     * Keeps an expand fetch on {@code anchor}'s calendar day, hides the extra probe line, and marks the new edge.
     * {@code extra} is a count of filter-matching lines, not file indices.
     */
    public static List<DisplayRow> forExpand(List<DisplayRow> fetched, DisplayRow anchor, boolean olderInFile,
                                             int extra, boolean oldestFirst) {
        return forExpand(fetched, anchor, olderInFile, extra, oldestFirst, List.of());
    }

    /**
     * Same as {@link #forExpand(List, DisplayRow, boolean, int, boolean)}. {@code already} is the open page.
     * When the probe is the only line between the new edge and a row already on that page, it is kept and no
     * caret is added.
     */
    public static List<DisplayRow> forExpand(List<DisplayRow> fetched, DisplayRow anchor, boolean olderInFile,
                                             int extra, boolean oldestFirst, List<DisplayRow> already) {
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
        if (probeFillsLoadedGap(peek, far, already, olderInFile)) {
            kept.add(peek);
            return List.copyOf(kept);
        }
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
        return clearCaretsFacingLoadedLines(clearClosedGaps(DisplayRows.mergeSorted(cleared, expanded, sort)));
    }

    /**
     * Probe lines that already fill the hole between two visible rows. Hiding them draws an expand caret for a
     * message the page fetched, and when the hole is a single line the nearer-edge tie keeps only the down caret.
     */
    private static Set<DisplayRow> coveredGapProbes(List<DisplayRow> visible, List<DisplayRow> probes) {
        if (visible.size() < 2 || probes.isEmpty()) return Set.of();
        Map<LogDay, List<DisplayRow>> visibleByDay = new HashMap<>();
        Map<LogDay, List<DisplayRow>> probesByDay = new HashMap<>();
        for (DisplayRow row : visible) {
            visibleByDay.computeIfAbsent(logDay(row), key -> new ArrayList<>()).add(row);
        }
        for (DisplayRow row : probes) {
            probesByDay.computeIfAbsent(logDay(row), key -> new ArrayList<>()).add(row);
        }
        Set<DisplayRow> covered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<LogDay, List<DisplayRow>> entry : probesByDay.entrySet()) {
            List<DisplayRow> dayVisible = visibleByDay.get(entry.getKey());
            if (dayVisible == null || dayVisible.size() < 2) continue;
            List<DisplayRow> sortedVisible = new ArrayList<>(dayVisible);
            sortedVisible.sort(Comparator.comparingInt(DisplayRow::lineIndex));
            List<DisplayRow> sortedProbes = new ArrayList<>(entry.getValue());
            sortedProbes.sort(Comparator.comparingInt(DisplayRow::lineIndex));
            int probeIndex = 0;
            for (int i = 1; i < sortedVisible.size(); i++) {
                int left = sortedVisible.get(i - 1).lineIndex();
                int right = sortedVisible.get(i).lineIndex();
                while (probeIndex < sortedProbes.size() && sortedProbes.get(probeIndex).lineIndex() <= left) {
                    probeIndex++;
                }
                int start = probeIndex;
                while (probeIndex < sortedProbes.size() && sortedProbes.get(probeIndex).lineIndex() < right) {
                    probeIndex++;
                }
                if (probeIndex - start != right - left - 1) continue;
                int expected = left + 1;
                boolean full = true;
                for (int probe = start; probe < probeIndex; probe++) {
                    if (sortedProbes.get(probe).lineIndex() != expected) {
                        full = false;
                        break;
                    }
                    expected++;
                }
                if (!full) continue;
                for (int probe = start; probe < probeIndex; probe++) {
                    covered.add(sortedProbes.get(probe));
                }
            }
        }
        return covered;
    }

    private static boolean probeFillsLoadedGap(DisplayRow peek, DisplayRow far, List<DisplayRow> already,
                                               boolean olderInFile) {
        if (peek == null || far == null || already == null || already.isEmpty() || !far.sameLog(peek)) return false;
        int inner = olderInFile ? peek.lineIndex() + 1 : peek.lineIndex() - 1;
        if (far.lineIndex() != inner) return false;
        int outer = olderInFile ? peek.lineIndex() - 1 : peek.lineIndex() + 1;
        LocalDate day = peek.entry().timestamp().toLocalDate();
        for (DisplayRow row : already) {
            if (!row.sameLog(peek) || row.lineIndex() != outer) continue;
            if (row.entry().timestamp().toLocalDate().equals(day)) return true;
        }
        return false;
    }

    private static LogDay logDay(DisplayRow row) {
        return new LogDay(row.chatLog().source(), row.entry().timestamp().toLocalDate());
    }

    private record LogDay(LogSource source, LocalDate day) {
    }

    /**
     * Drops a caret when the next stored line in that direction is already on the page. Merging two searches
     * can leave the flag from the page that had not yet loaded that neighbour, and a line-index hole or another
     * log sitting between them would still draw the triangle even though expanding cannot reveal anything new.
     */
    public static List<DisplayRow> clearCaretsFacingLoadedLines(List<DisplayRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        Map<LogSource, Set<Integer>> linesBySource = new HashMap<>();
        for (DisplayRow row : rows) {
            linesBySource.computeIfAbsent(row.chatLog().source(), key -> new HashSet<>()).add(row.lineIndex());
        }
        List<DisplayRow> cleared = new ArrayList<>(rows.size());
        for (DisplayRow row : rows) {
            Set<Integer> lines = linesBySource.get(row.chatLog().source());
            boolean up = row.expandUp() && (lines == null || !lines.contains(row.lineIndex() - 1));
            boolean down = row.expandDown() && (lines == null || !lines.contains(row.lineIndex() + 1));
            cleared.add(row.withExpand(up, down));
        }
        return List.copyOf(cleared);
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
