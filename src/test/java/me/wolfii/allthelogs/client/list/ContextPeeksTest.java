package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class ContextPeeksTest {
    private static ChatLog log(String name) {
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        return new ChatLog(new LogSource.File(Path.of(name)), start.toLocalDate(), "26.2", start, start);
    }

    private static DisplayRow row(ChatLog log, int line, String message, boolean match) {
        return row(log, line, message, match, null);
    }

    private static DisplayRow row(ChatLog log, int line, String message, boolean match, String server) {
        return new DisplayRow(
            new ChatEntry(log, log.startTime().plusSeconds(line), line, message, null, null, server),
            match, List.of());
    }

    @Test
    void hidesTheExtraContextLineAndMarksTheVisibleEdge() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 0, "ctx0", false),
            row(log, 1, "ctx1", false),
            row(log, 2, "hit", true),
            row(log, 3, "ctx3", false),
            row(log, 4, "ctx4", false));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 1, true);
        assertEquals(List.of(1, 2, 3), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandUp());
        assertFalse(visible.getFirst().expandDown());
        assertTrue(visible.getLast().expandDown());
        assertFalse(visible.getLast().expandUp());
    }

    @Test
    void doesNotMarkAnEdgeWhenThePeekBelongsToAnotherDay() {
        ChatLog log = log("a.log");
        LocalDateTime day = LocalDateTime.of(2026, 8, 26, 0, 1);
        List<DisplayRow> rows = List.of(
            new DisplayRow(new ChatEntry(log, day.minusMinutes(2), 0, "prev"), false, List.of()),
            new DisplayRow(new ChatEntry(log, day, 1, "hit"), true, List.of()));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 0, true);
        assertEquals(1, visible.size());
        assertFalse(visible.getFirst().expandUp());
        assertFalse(visible.getFirst().expandDown());
    }

    @Test
    void expandFetchDropsTheProbeLineAndStaysOnTheSameDay() {
        ChatLog log = log("a.log");
        DisplayRow anchor = row(log, 10, "hit", true);
        List<DisplayRow> fetched = new ArrayList<>();
        for (int line = 10; line <= 21; line++) {
            fetched.add(row(log, line, "m" + line, line == 10));
        }
        fetched.add(new DisplayRow(new ChatEntry(log, LocalDateTime.of(2026, 8, 27, 0, 0), 22, "next-day"),
            false, List.of()));
        List<DisplayRow> kept = ContextPeeks.forExpand(fetched, anchor, false, 10);
        assertEquals(11, kept.size());
        assertEquals(20, kept.getLast().lineIndex());
        assertTrue(kept.getLast().expandDown());
        assertFalse(kept.getLast().expandUp());
    }

    @Test
    void shiftExpandFetchKeepsAHundredExtraLines() {
        ChatLog log = log("a.log");
        DisplayRow anchor = row(log, 10, "hit", true);
        int extra = MessageListLayout.SHIFT_EXPAND_LINES;
        List<DisplayRow> fetched = new ArrayList<>();
        for (int line = 10; line <= 10 + extra + 1; line++) {
            fetched.add(row(log, line, "m" + line, line == 10));
        }
        List<DisplayRow> kept = ContextPeeks.forExpand(fetched, anchor, false, extra);
        assertEquals(extra + 1, kept.size());
        assertEquals(10 + extra, kept.getLast().lineIndex());
        assertTrue(kept.getLast().expandDown());
    }

    @Test
    void mergeAfterExpandClearsTheFacingCaretOnTheNeighborWhenTheGapCloses() {
        ChatLog log = log("a.log");
        DisplayRow above = row(log, 0, "above", true).withExpand(false, true);
        DisplayRow below = row(log, 12, "below", true).withExpand(true, false);
        List<DisplayRow> filled = new ArrayList<>();
        for (int line = 0; line <= 12; line++) {
            filled.add(row(log, line, "m" + line, line == 0 || line == 12));
        }
        List<DisplayRow> merged = ContextPeeks.mergeAfterExpand(List.of(above, below), filled, below, true,
            ChatQuery.Sort.ASCENDING);
        assertEquals(13, merged.size());
        assertFalse(merged.getFirst().expandDown());
        assertFalse(merged.getLast().expandUp());
        assertEquals(0, MessageListLayout.of(merged, 4).separators().size());
    }

    @Test
    void mergeAfterExpandKeepsFacingCaretsWhenAGapRemains() {
        ChatLog log = log("a.log");
        DisplayRow above = row(log, 0, "above", true).withExpand(false, true);
        DisplayRow below = row(log, 20, "below", true).withExpand(true, false);
        DisplayRow extra = row(log, 19, "near-below", false).withExpand(true, false);
        List<DisplayRow> merged = ContextPeeks.mergeAfterExpand(List.of(above, below),
            List.of(extra, below), below, true, ChatQuery.Sort.ASCENDING);
        assertEquals(3, merged.size());
        assertTrue(merged.getFirst().expandDown());
        assertTrue(merged.get(1).expandUp());
    }

    @Test
    void mergeAfterExpandClearsTheUsedCaretOnTheAnchor() {
        ChatLog log = log("a.log");
        DisplayRow anchor = row(log, 10, "hit", true).withExpand(false, true);
        DisplayRow extra = row(log, 11, "next", false).withExpand(false, true);
        List<DisplayRow> merged = ContextPeeks.mergeAfterExpand(List.of(anchor), List.of(anchor, extra),
            anchor, false, ChatQuery.Sort.ASCENDING);
        assertEquals(2, merged.size());
        assertFalse(merged.getFirst().expandDown());
        assertTrue(merged.getLast().expandDown());
    }

    @Test
    void unfilteredPagesAreLeftAlone() {
        List<DisplayRow> rows = DisplayRow.from(List.of(
                new ChatEntry(log("a.log"), LocalDateTime.of(2026, 8, 26, 10, 0), 0, "a")),
            SearchFilter.defaults());
        assertEquals(rows, ContextPeeks.strip(rows, 4, false));
    }

    @Test
    void filterBarWithoutSearchDoesNotMarkExpand() {
        ChatLog log = log("a.log");
        DisplayRow first = row(log, 0, "a", true);
        DisplayRow later = row(log, 20, "b", true);
        List<DisplayRow> rows = List.of(first, later);
        List<DisplayRow> visible = ContextPeeks.forSearchPage(rows, false, 4);
        assertEquals(rows, visible);
        assertFalse(visible.getFirst().expandDown());
        assertFalse(visible.getLast().expandUp());
        assertEquals(0, MessageListLayout.of(visible, 4).separators().size());
    }

    @Test
    void unfilteredGapsAreNotMarkedForExpand() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(row(log, 0, "a", true), row(log, 20, "b", true));
        assertEquals(rows, ContextPeeks.strip(rows, 4, false));
        assertEquals(rows, ContextPeeks.forSearchPage(rows, false, 4));
        assertFalse(rows.getFirst().expandDown());
        assertFalse(rows.getLast().expandUp());
    }

    @Test
    void textSearchStillMarksExpandAtClusterEdges() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 0, "ctx0", false),
            row(log, 1, "ctx1", false),
            row(log, 2, "hit", true),
            row(log, 3, "ctx3", false),
            row(log, 4, "ctx4", false));
        List<DisplayRow> visible = ContextPeeks.forSearchPage(rows, true, 1);
        assertEquals(List.of(1, 2, 3), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandUp());
        assertTrue(visible.getLast().expandDown());
    }

    @Test
    void stripCountsFetchedNeighboursNotFileLineGaps() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 0, "far before", false),
            row(log, 5, "near before", false),
            row(log, 10, "hit", true),
            row(log, 15, "near after", false),
            row(log, 20, "far after", false));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 1, true);
        assertEquals(List.of(5, 10, 15), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandUp());
        assertTrue(visible.getLast().expandDown());
    }

    @Test
    void expandCountsFilterMatchingLinesNotFileIndices() {
        ChatLog log = log("a.log");
        DisplayRow anchor = row(log, 10, "on hypixel", true, "hypixel.net");
        SearchFilter filter = SearchFilter.defaults().withServerOrWorld("hypixel");
        List<DisplayRow> fetched = List.of(
            anchor,
            row(log, 11, "other", false, "gommehd.net"),
            row(log, 12, "still hypixel", false, "mc.hypixel.net"),
            row(log, 13, "peek", false, "hypixel.net"));
        List<DisplayRow> allowed = fetched.stream().filter(row -> filter.allowsContext(row.entry())).toList();
        List<DisplayRow> kept = ContextPeeks.forExpand(allowed, anchor, false, 1);
        assertEquals(List.of(10, 12), kept.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(kept.getLast().expandDown());
    }

    @Test
    void dropsACaretWhenTheNextStoredLineIsAlreadyOnThePage() {
        ChatLog log = log("a.log");
        ChatLog other = log("b.log");
        DisplayRow edge = row(log, 10, "edge", true).withExpand(false, true);
        DisplayRow foreign = row(other, 1, "other session", true);
        DisplayRow next = row(log, 11, "already loaded", false);
        List<DisplayRow> merged = DisplayRows.mergeUnique(List.of(edge), List.of(row(log, 10, "edge", true), next));
        merged = new ArrayList<>(merged);
        merged.add(1, foreign);
        List<DisplayRow> shown = ContextPeeks.clearCaretsFacingLoadedLines(merged);
        assertFalse(shown.getFirst().expandDown());
        assertEquals(0, MessageListLayout.of(shown, 3).separators().stream()
            .filter(separator -> separator.expandUp() || separator.expandDown()).count());
    }

    @Test
    void keepsACaretWhenTheNeighbourIsStillHidden() {
        ChatLog log = log("a.log");
        DisplayRow edge = row(log, 10, "edge", true).withExpand(false, true);
        DisplayRow later = row(log, 14, "later", true).withExpand(true, false);
        List<DisplayRow> shown = ContextPeeks.clearCaretsFacingLoadedLines(List.of(edge, later));
        assertTrue(shown.getFirst().expandDown());
        assertTrue(shown.getLast().expandUp());
        assertEquals(1, MessageListLayout.of(shown, 3).separators().size());
    }

    @Test
    void marksBothCaretsWhenTheSameRowHasAProbeOnEachSide() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 9, "before", false),
            row(log, 10, "hit", true),
            row(log, 11, "after", false),
            row(log, 14, "later", true));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 0, true);
        assertEquals(List.of(10, 14), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandUp());
        assertTrue(visible.getFirst().expandDown());
        assertFalse(visible.getLast().expandUp());
    }

    @Test
    void marksBothSidesWhenAProbeSitsHalfwayAcrossARealGap() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 10, "hit", true),
            row(log, 15, "middle", false),
            row(log, 20, "hit", true));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 0, true);
        assertEquals(List.of(10, 20), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandDown());
        assertFalse(visible.getFirst().expandUp());
        assertTrue(visible.getLast().expandUp());
        assertFalse(visible.getLast().expandDown());
    }

    @Test
    void showsEveryFetchedLineThatFillsAGap() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 9, "before", false),
            row(log, 10, "hit", true),
            row(log, 11, "gap", false),
            row(log, 12, "gap", false),
            row(log, 13, "hit", true),
            row(log, 14, "after", false));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 0, true);
        assertEquals(List.of(10, 11, 12, 13), visible.stream().map(DisplayRow::lineIndex).toList());
        assertFalse(visible.get(1).expandUp() || visible.get(1).expandDown());
        assertFalse(visible.get(2).expandUp() || visible.get(2).expandDown());
        assertTrue(visible.getFirst().expandUp());
        assertFalse(visible.getFirst().expandDown());
        assertTrue(visible.getLast().expandDown());
    }

    @Test
    void showsAProbeThatFillsTheGapToARowAlreadyOnThePage() {
        ChatLog log = log("a.log");
        DisplayRow already = row(log, 10, "loaded", true);
        List<DisplayRow> rows = List.of(
            row(log, 11, "between", false),
            row(log, 12, "hit", true));
        List<DisplayRow> visible = ContextPeeks.forSearchPage(rows, true, 0, List.of(already));
        assertEquals(List.of(11, 12), visible.stream().map(DisplayRow::lineIndex).toList());
        assertFalse(visible.getFirst().expandUp());
        assertFalse(visible.getLast().expandUp());
    }

    @Test
    void expandKeepsTheProbeWhenItIsTheOnlyLineBeforeALoadedRow() {
        ChatLog log = log("a.log");
        DisplayRow anchor = row(log, 10, "hit", true);
        DisplayRow loaded = row(log, 13, "already", true);
        List<DisplayRow> fetched = List.of(
            anchor,
            row(log, 11, "new", false),
            row(log, 12, "probe", false));
        List<DisplayRow> kept = ContextPeeks.forExpand(fetched, anchor, false, 1, List.of(anchor, loaded));
        assertEquals(List.of(10, 11, 12), kept.stream().map(DisplayRow::lineIndex).toList());
        assertFalse(kept.get(1).expandDown());
        assertFalse(kept.getLast().expandDown());
    }

    @Test
    void showsTheSingleFetchedLineBetweenTwoClusters() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 9, "before", false),
            row(log, 10, "hit", true),
            row(log, 11, "between", false),
            row(log, 12, "hit", true),
            row(log, 13, "after", false));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 0, true);
        assertEquals(List.of(10, 11, 12), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandUp());
        assertFalse(visible.getFirst().expandDown());
        assertFalse(visible.get(1).expandUp());
        assertFalse(visible.get(1).expandDown());
        assertTrue(visible.getLast().expandDown());
        List<MessageListLayout.Separator> carets = MessageListLayout.of(visible, 0).separators().stream()
            .filter(separator -> separator.expandUp() || separator.expandDown()).toList();
        assertEquals(2, carets.size());
        assertTrue(carets.getFirst().expandUp());
        assertFalse(carets.getFirst().expandDown());
        assertTrue(carets.getLast().expandDown());
        assertFalse(carets.getLast().expandUp());
    }

    @Test
    void keepsTheDownCaretWhenTheGapIsMoreThanTheFetchedProbe() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = List.of(
            row(log, 10, "hit", true),
            row(log, 11, "probe", false),
            row(log, 13, "hit", true),
            row(log, 14, "after", false));
        List<DisplayRow> visible = ContextPeeks.strip(rows, 0, true);
        assertEquals(List.of(10, 13), visible.stream().map(DisplayRow::lineIndex).toList());
        assertTrue(visible.getFirst().expandDown());
        assertFalse(visible.getLast().expandUp());
    }

    /**
     * Search {@code 0bcn} on 2026-08-30: sixteen hits in one session, context 3. Line 342 is the only
     * fetched message between the clusters around it, so it stays visible and does not grow a down caret.
     */
    @Test
    void august30SearchShowsTheOneLineGapAndKeepsRealCarets() {
        ChatLog log = log("session.log");
        int[] hits = {283, 287, 299, 305, 318, 319, 326, 328, 331, 337, 338, 346, 347, 348, 352, 353};
        Set<Integer> hitSet = new HashSet<>();
        for (int hit : hits) hitSet.add(hit);
        Set<Integer> fetched = new TreeSet<>();
        for (int hit : hits) {
            for (int delta = -4; delta <= 4; delta++) fetched.add(hit + delta);
        }
        List<DisplayRow> rows = new ArrayList<>();
        for (int line : fetched) {
            rows.add(row(log, line, "m" + line, hitSet.contains(line)));
        }
        List<DisplayRow> visible = ContextPeeks.strip(rows, 3, true);
        assertEquals(342, rowAt(visible, 342).lineIndex());
        assertFalse(rowAt(visible, 341).expandDown());
        assertFalse(rowAt(visible, 343).expandUp());
        assertEquals(List.of(280, 290, 296, 308, 315, 356), carets(visible));
        assertTrue(rowAt(visible, 280).expandUp());
        assertTrue(rowAt(visible, 290).expandDown());
        assertTrue(rowAt(visible, 296).expandUp());
        assertTrue(rowAt(visible, 308).expandDown());
        assertTrue(rowAt(visible, 315).expandUp());
        assertTrue(rowAt(visible, 356).expandDown());
    }

    private static DisplayRow rowAt(List<DisplayRow> rows, int line) {
        for (DisplayRow row : rows) {
            if (row.lineIndex() == line) return row;
        }
        throw new AssertionError("missing line " + line);
    }

    private static List<Integer> carets(List<DisplayRow> rows) {
        List<Integer> lines = new ArrayList<>();
        for (DisplayRow row : rows) {
            if (row.expandUp() || row.expandDown()) lines.add(row.lineIndex());
        }
        return lines;
    }
}
