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
    public static List<DisplayRow> forSearchPage(List<DisplayRow> rows, boolean hasText, int contextLines) {
        return forSearchPage(rows, hasText, contextLines, List.of());
    }

    /**
     * Same as {@link #forSearchPage(List, boolean, int)}, treating {@code neighbors} as rows already
     * on the page. A probe that fills the hole between those rows and this page is shown instead of becoming
     * a caret. Scrolling and a search that keeps the viewport fetch each side alone, so the shared boundary
     * line would otherwise stay hidden.
     */
    public static List<DisplayRow> forSearchPage(List<DisplayRow> rows, boolean hasText, int contextLines,
                                                 List<DisplayRow> neighbors) {
        if (rows == null) return List.of();
        if (hasText) {
            return strip(rows, contextLines, true, neighbors);
        }
        return List.copyOf(rows);
    }

    /**
     * Drops lines that are only there to detect more content, and marks the visible edges that can expand.
     * Distance is the number of fetched same-log rows to the nearest hit, so other-server neighbours that
     * were skipped in SQL do not consume the context budget.
     */
    public static List<DisplayRow> strip(List<DisplayRow> rows, int contextLines, boolean hasText) {
        return strip(rows, contextLines, hasText, List.of());
    }

    static List<DisplayRow> strip(List<DisplayRow> rows, int contextLines, boolean hasText,
                                  List<DisplayRow> neighbors) {
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
        int probeDistance = contextLines + 1;
        Map<LogDay, List<DisplayRow>> chains = chains(gapVisible, probes);
        Set<DisplayRow> covered = coveredGapProbes(chains, rankFromHit, probeDistance);
        List<DisplayRow> visible = new ArrayList<>();
        List<DisplayRow> peeks = new ArrayList<>();
        for (DisplayRow row : rows) {
            int distance = rankFromHit.getOrDefault(row, Integer.MAX_VALUE);
            if (row.match() || distance <= contextLines || covered.contains(row)) {
                visible.add(row);
            } else if (distance == probeDistance) {
                peeks.add(row);
            }
        }
        for (DisplayRow peek : peeks) {
            markNearestEdges(visible, peek, chains.get(logDay(peek)), rankFromHit, probeDistance);
        }
        return List.copyOf(visible);
    }

    /**
     * Fetched rows of one log and day, oldest line first. Rows already on the page are included so a probe
     * can fill the hole up to them.
     */
    private static Map<LogDay, List<DisplayRow>> chains(List<DisplayRow> visible, List<DisplayRow> probes) {
        Map<LogDay, List<DisplayRow>> chains = new HashMap<>();
        for (DisplayRow row : visible) {
            if (row != null) chains.computeIfAbsent(logDay(row), key -> new ArrayList<>()).add(row);
        }
        for (DisplayRow row : probes) {
            if (row != null) chains.computeIfAbsent(logDay(row), key -> new ArrayList<>()).add(row);
        }
        for (List<DisplayRow> chain : chains.values()) {
            chain.sort(Comparator.comparingInt(DisplayRow::lineIndex));
        }
        return chains;
    }

    /**
     * Every visible row at the closest stored-chat distance gets the caret. Distance walks the fetched
     * lines, so a line-index hole left by dedup does not pull the caret onto only the nearer index.
     * A tie used to keep only the earlier row, so a gap showed a down arrow and no up arrow. A second
     * probe on the same row also has to update the row already replaced by the first probe; matching the
     * original instance misses it once its flags change.
     */
    private static void markNearestEdges(List<DisplayRow> visible, DisplayRow peek, List<DisplayRow> chain,
                                         Map<DisplayRow, Integer> rank, int probeDistance) {
        if (chain == null || chain.isEmpty()) return;
        int peekAt = -1;
        Map<Integer, Integer> position = new HashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            DisplayRow row = chain.get(i);
            if (row == peek) peekAt = i;
            position.putIfAbsent(row.lineIndex(), i);
        }
        if (peekAt < 0) {
            Integer fallback = position.get(peek.lineIndex());
            if (fallback == null) return;
            peekAt = fallback;
        }
        LocalDate peekDay = peek.entry().timestamp().toLocalDate();
        int best = Integer.MAX_VALUE;
        List<Integer> edges = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            DisplayRow row = visible.get(i);
            if (!row.sameLog(peek)) continue;
            if (!row.entry().timestamp().toLocalDate().equals(peekDay)) continue;
            Integer at = position.get(row.lineIndex());
            if (at == null || at == peekAt) continue;
            int gap = storedDistance(chain, peekAt, at, rank, probeDistance);
            if (gap <= 0 || gap > best) continue;
            if (gap < best) {
                best = gap;
                edges.clear();
            }
            edges.add(i);
        }
        for (int index : edges) {
            DisplayRow edge = visible.get(index);
            boolean moreBefore = edge.lineIndex() > peek.lineIndex();
            visible.set(index, addFileExpand(edge, moreBefore, !moreBefore));
        }
    }

    /**
     * Steps along the fetched chain. A stored neighbour counts as one chat line. A hole between two probe
     * rows counts as the raw index span, because chat outside every context window was not fetched.
     */
    private static int storedDistance(List<DisplayRow> chain, int from, int to, Map<DisplayRow, Integer> rank,
                                     int probeDistance) {
        int step = from < to ? 1 : -1;
        int distance = 0;
        for (int i = from; i != to; i += step) {
            DisplayRow first = chain.get(i);
            DisplayRow second = chain.get(i + step);
            if (storedAdjacent(first, second, rank, probeDistance)) {
                distance++;
            } else {
                distance += Math.abs(second.lineIndex() - first.lineIndex());
            }
        }
        return distance;
    }

    /**
     * Nothing stored sits between these two fetched rows. Consecutive indices always touch. A line-index
     * hole inside a context window is a row dedup removed, so the next fetched line is still the next chat
     * line. A hole with a probe on both sides can hide chat that no window reached.
     */
    private static boolean storedAdjacent(DisplayRow left, DisplayRow right, Map<DisplayRow, Integer> rank,
                                         int probeDistance) {
        if (!left.sameLog(right)) return false;
        if (Math.abs(right.lineIndex() - left.lineIndex()) <= 1) return true;
        int leftRank = rank.getOrDefault(left, Integer.MAX_VALUE);
        int rightRank = rank.getOrDefault(right, Integer.MAX_VALUE);
        if (leftRank == Integer.MAX_VALUE || rightRank == Integer.MAX_VALUE) return false;
        return leftRank < probeDistance || rightRank < probeDistance;
    }

    /**
     * Keeps an expand fetch on {@code anchor}'s calendar day, hides the extra probe line, and marks the new edge.
     * {@code extra} is a count of filter-matching lines, not file indices.
     */
    public static List<DisplayRow> forExpand(List<DisplayRow> fetched, DisplayRow anchor, boolean olderInFile,
                                             int extra) {
        return forExpand(fetched, anchor, olderInFile, extra, List.of());
    }

    /**
     * Same as {@link #forExpand(List, DisplayRow, boolean, int)}. {@code already} is the open page.
     * When the probe is the only line between the new edge and a row already on that page, it is kept and no
     * caret is added.
     */
    public static List<DisplayRow> forExpand(List<DisplayRow> fetched, DisplayRow anchor, boolean olderInFile,
                                             int extra, List<DisplayRow> already) {
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
                kept.set(i, addFileExpand(row, olderInFile, !olderInFile));
            }
        }
        return List.copyOf(kept);
    }

    /**
     * Merges an expand fetch into the open page and clears the caret that was just used on the anchor.
     */
    public static List<DisplayRow> mergeAfterExpand(List<DisplayRow> existing, List<DisplayRow> expanded,
                                                    DisplayRow anchor, boolean olderInFile, ChatQuery.Sort sort) {
        List<DisplayRow> cleared = new ArrayList<>(existing.size());
        for (DisplayRow row : existing) {
            if (anchor != null && row.key().equals(anchor.key())) {
                cleared.add(clearExpandedSide(row, olderInFile));
            } else {
                cleared.add(row);
            }
        }
        return clearCaretsFacingLoadedLines(clearClosedGaps(DisplayRows.mergeSorted(cleared, expanded, sort)));
    }

    /**
     * Probe lines that already fill the hole between two visible rows. Hiding them draws an expand caret for a
     * message the page fetched. The hole is measured in stored chat, not raw line indices: dedup deletes rows
     * and leaves holes, and those missing indices are not chat the page failed to fetch.
     */
    private static Set<DisplayRow> coveredGapProbes(Map<LogDay, List<DisplayRow>> chains,
                                                   Map<DisplayRow, Integer> rank, int probeDistance) {
        Set<DisplayRow> covered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<DisplayRow> chain : chains.values()) {
            List<Integer> anchors = new ArrayList<>();
            for (int i = 0; i < chain.size(); i++) {
                int distance = rank.getOrDefault(chain.get(i), Integer.MAX_VALUE);
                if (distance != probeDistance) anchors.add(i);
            }
            for (int a = 1; a < anchors.size(); a++) {
                int left = anchors.get(a - 1);
                int right = anchors.get(a);
                if (right == left + 1) continue;
                if (!filledStoredGap(chain, left, right, rank, probeDistance)) continue;
                for (int i = left + 1; i < right; i++) covered.add(chain.get(i));
            }
        }
        return covered;
    }

    private static boolean filledStoredGap(List<DisplayRow> chain, int left, int right,
                                          Map<DisplayRow, Integer> rank, int probeDistance) {
        for (int i = left; i < right; i++) {
            if (!storedAdjacent(chain.get(i), chain.get(i + 1), rank, probeDistance)) return false;
        }
        return true;
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

    /**
     * The list is oldest at the top, so a line earlier in the file is list-up and a later line is list-down.
     */
    static DisplayRow addFileExpand(DisplayRow row, boolean moreBefore, boolean moreAfter) {
        boolean up = row.expandUp();
        boolean down = row.expandDown();
        if (moreBefore) up = true;
        if (moreAfter) down = true;
        return row.withExpand(up, down);
    }

    private static DisplayRow clearExpandedSide(DisplayRow row, boolean olderInFile) {
        boolean up = row.expandUp();
        boolean down = row.expandDown();
        if (olderInFile) up = false;
        else down = false;
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
