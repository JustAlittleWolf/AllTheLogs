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
        markEdges(visible, peeks, chains, rankFromHit, probeDistance);
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
     * Marks the nearest shown row on each side of every hidden probe. Stored distance only grows as the
     * walk leaves the probe, so that row is the first shown line in that direction. The chain is indexed
     * once; each probe then stops at the first shown row instead of measuring every visible row.
     * <p>
     * A tie marks both sides. The caret records the probe's line index, so a later page can tell that
     * hidden line from a different cluster's probe. A second probe on the same row updates the row already
     * replaced by the first probe.
     */
    private static void markEdges(List<DisplayRow> visible, List<DisplayRow> peeks,
                                 Map<LogDay, List<DisplayRow>> chains, Map<DisplayRow, Integer> rank,
                                 int probeDistance) {
        Map<LogDay, ChainIndex> indexes = new HashMap<>();
        for (DisplayRow peek : peeks) {
            LogDay day = logDay(peek);
            ChainIndex index = indexes.get(day);
            if (index == null) {
                index = ChainIndex.of(chains.get(day), visible, day);
                indexes.put(day, index);
            }
            markNearestEdge(visible, peek, index, rank, probeDistance);
        }
    }

    private static void markNearestEdge(List<DisplayRow> visible, DisplayRow peek, ChainIndex index,
                                       Map<DisplayRow, Integer> rank, int probeDistance) {
        if (index == null || index.chain.isEmpty()) return;
        int peekAt = index.positionOf(peek);
        if (peekAt < 0) return;
        ChainIndex.Side older = index.nearestShown(peekAt, -1, rank, probeDistance);
        ChainIndex.Side newer = index.nearestShown(peekAt, 1, rank, probeDistance);
        if (older.index >= 0 && older.distance <= newer.distance) {
            markShown(visible, index, peek, older.index);
        }
        if (newer.index >= 0 && newer.distance <= older.distance) {
            markShown(visible, index, peek, newer.index);
        }
    }

    private static void markShown(List<DisplayRow> visible, ChainIndex index, DisplayRow peek, int at) {
        Integer visibleIndex = index.visibleIndex.get(index.chain.get(at).lineIndex());
        if (visibleIndex == null) return;
        DisplayRow edge = visible.get(visibleIndex);
        boolean moreBefore = edge.lineIndex() > peek.lineIndex();
        visible.set(visibleIndex, addFileExpand(edge, moreBefore, !moreBefore, peek.lineIndex()));
    }

    /**
     * One log-day chain, with shown rows flagged so a probe walk can stop at the first one.
     */
    private static final class ChainIndex {
        private final List<DisplayRow> chain;
        private final boolean[] shown;
        private final Map<Integer, Integer> position = new HashMap<>();
        private final Map<DisplayRow, Integer> byIdentity = new IdentityHashMap<>();
        private final Map<Integer, Integer> visibleIndex = new HashMap<>();

        private ChainIndex(List<DisplayRow> chain, boolean[] shown) {
            this.chain = chain;
            this.shown = shown;
            for (int i = 0; i < chain.size(); i++) {
                DisplayRow row = chain.get(i);
                position.putIfAbsent(row.lineIndex(), i);
                byIdentity.putIfAbsent(row, i);
            }
        }

        private static final class Side {
            private final int index;
            private final int distance;

            private Side(int index, int distance) {
                this.index = index;
                this.distance = distance;
            }
        }

        private static ChainIndex of(List<DisplayRow> chain, List<DisplayRow> visible, LogDay day) {
            if (chain == null || chain.isEmpty()) return new ChainIndex(List.of(), new boolean[0]);
            boolean[] shown = new boolean[chain.size()];
            ChainIndex index = new ChainIndex(chain, shown);
            for (int i = 0; i < visible.size(); i++) {
                DisplayRow row = visible.get(i);
                if (!day.source().equals(row.chatLog().source())) continue;
                if (!day.day().equals(row.entry().timestamp().toLocalDate())) continue;
                Integer at = index.position.get(row.lineIndex());
                if (at == null) continue;
                shown[at] = true;
                index.visibleIndex.putIfAbsent(row.lineIndex(), i);
            }
            return index;
        }

        private int positionOf(DisplayRow peek) {
            Integer at = byIdentity.get(peek);
            if (at != null) return at;
            Integer fallback = position.get(peek.lineIndex());
            return fallback == null ? -1 : fallback;
        }

        /**
         * First shown row in {@code step}'s direction. Stored distance only grows along the walk, so that
         * row is the nearest. A stored neighbour counts as one line. A hole between two probe rows counts
         * as the raw index span, because chat outside every window was not fetched.
         */
        private Side nearestShown(int from, int step, Map<DisplayRow, Integer> rank, int probeDistance) {
            int distance = 0;
            for (int i = from; i + step >= 0 && i + step < chain.size(); i += step) {
                DisplayRow first = chain.get(i);
                DisplayRow second = chain.get(i + step);
                if (storedAdjacent(first, second, rank, probeDistance)) {
                    distance++;
                } else {
                    distance += Math.abs(second.lineIndex() - first.lineIndex());
                }
                if (shown[i + step]) return new Side(i + step, distance);
            }
            return new Side(-1, Integer.MAX_VALUE);
        }
    }

    /**
     * Nothing stored sits between these two fetched rows. Consecutive indices always touch. A line-index
     * hole inside a context window is a row dedup removed, so the next fetched line is still the next chat
     * line. A hole with a probe on both sides can hide chat that no window reached. A row already on the
     * page is not inside this fetch, so it only touches the hidden line its own caret recorded.
     */
    private static boolean storedAdjacent(DisplayRow left, DisplayRow right, Map<DisplayRow, Integer> rank,
                                         int probeDistance) {
        if (!left.sameLog(right)) return false;
        if (Math.abs(right.lineIndex() - left.lineIndex()) <= 1) return true;
        int leftRank = rank.getOrDefault(left, Integer.MAX_VALUE);
        int rightRank = rank.getOrDefault(right, Integer.MAX_VALUE);
        if (leftRank == Integer.MAX_VALUE || rightRank == Integer.MAX_VALUE) {
            return facesRecordedProbe(left, right) || facesRecordedProbe(right, left);
        }
        return leftRank < probeDistance || rightRank < probeDistance;
    }

    /**
     * {@code edge} was already shown, and {@code probe} is the hidden line its caret was aimed at.
     */
    private static boolean facesRecordedProbe(DisplayRow edge, DisplayRow probe) {
        if (edge.lineIndex() < probe.lineIndex()) {
            return edge.expandDown() && edge.probeBelow() == probe.lineIndex();
        }
        return edge.expandUp() && edge.probeAbove() == probe.lineIndex();
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
                kept.set(i, addFileExpand(row, olderInFile, !olderInFile, peek.lineIndex()));
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
            boolean up = row.expandUp() && !loadedToward(lines, row.lineIndex() - 1, row.probeAbove());
            boolean down = row.expandDown() && !loadedToward(lines, row.lineIndex() + 1, row.probeBelow());
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
            if (!storedTouch(previous, current)) continue;
            out[i - 1] = previous.withExpand(previous.expandUp(), false);
            out[i] = current.withExpand(false, current.expandDown());
        }
        return List.of(out);
    }

    /**
     * The list is oldest at the top, so a line earlier in the file is list-up and a later line is list-down.
     */
    static DisplayRow addFileExpand(DisplayRow row, boolean moreBefore, boolean moreAfter, int probeLine) {
        boolean up = row.expandUp();
        boolean down = row.expandDown();
        int above = row.probeAbove();
        int below = row.probeBelow();
        if (moreBefore) {
            up = true;
            // Two peeks can share an edge. The nearer one has the higher line index.
            if (above < 0 || probeLine > above) above = probeLine;
        }
        if (moreAfter) {
            down = true;
            // The nearer peek below the edge has the lower line index.
            if (below < 0 || probeLine < below) below = probeLine;
        }
        return row.withExpand(up, down, above, below);
    }

    /**
     * The next stored line in this direction is already on the page. {@code adjacentIndex} is the
     * neighbouring line number; {@code probeLine} is the hidden line a caret recorded, which can sit
     * further away when dedup left a hole.
     */
    private static boolean loadedToward(Set<Integer> lines, int adjacentIndex, int probeLine) {
        if (lines == null) return false;
        if (lines.contains(adjacentIndex)) return true;
        return probeLine >= 0 && lines.contains(probeLine);
    }

    private static boolean storedTouch(DisplayRow previous, DisplayRow current) {
        if (Math.abs(current.lineIndex() - previous.lineIndex()) <= 1) return true;
        return previous.probeBelow() == current.lineIndex() || current.probeAbove() == previous.lineIndex();
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
