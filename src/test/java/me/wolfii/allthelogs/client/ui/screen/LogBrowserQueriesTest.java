package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.PageBounds;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.MatchDay;
import me.wolfii.allthelogs.data.MatchSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class LogBrowserQueriesTest {
    private static DisplayRow row(LocalDateTime time, int line) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        return new DisplayRow(new ChatEntry(log, time, line, "msg"), true, List.of());
    }

    @Test
    void continueFromKeepsTheEdgeSecondByExcludingOnlyTheEdgeRow() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        DisplayRow edge = row(time, 40);
        ChatQuery next = PageBounds.continueFrom(ChatQuery.all().withLimit(100), edge);
        assertEquals(time, next.offset());
        assertEquals(edge.chatLog().source(), next.offsetSource());
        assertEquals(40, next.offsetLine());
        assertEquals(0, next.skip());
        ChatQuery previous = PageBounds.continueFrom(
            ChatQuery.all().withSort(ChatQuery.Sort.DESCENDING).withLimit(100), edge);
        assertEquals(time, previous.offset());
        assertEquals(edge.chatLog().source(), previous.offsetSource());
        assertEquals(40, previous.offsetLine());
        assertEquals(ChatQuery.Sort.DESCENDING, previous.sort());
    }

    @Test
    void exclusiveOffsetIncludesTheTargetTimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        assertEquals(time.plusNanos(1_000), PageBounds.exclusiveOffset(time, ChatQuery.Sort.DESCENDING));
        assertEquals(time.minusNanos(1_000), PageBounds.exclusiveOffset(time, ChatQuery.Sort.ASCENDING));
    }

    @Test
    void chronologicalPagesHaveOlderRowsBeforeAndNewerRowsAfter() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        LocalDateTime last = first.plusMinutes(1);
        List<DisplayRow> rows = List.of(row(first, 0), row(last, 1));
        MatchSummary summary = new MatchSummary(first.minusHours(1), last.plusHours(1), 1, List.of());
        assertTrue(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows, summary));
        assertTrue(PageBounds.hasAfter(ChatQuery.Sort.ASCENDING, false, rows, summary));
        assertFalse(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows,
            new MatchSummary(first, last, 1, List.of())));
        assertTrue(PageBounds.hasAfter(ChatQuery.Sort.ASCENDING, true, rows,
            new MatchSummary(first, last, 1, List.of())));
    }

    @Test
    void newestFirstPagesHaveNewerRowsBeforeAndOlderRowsAfter() {
        LocalDateTime newest = LocalDateTime.of(2026, 8, 27, 12, 0, 0);
        LocalDateTime oldest = newest.minusMinutes(1);
        List<DisplayRow> rows = List.of(row(newest, 1), row(oldest, 0));
        MatchSummary summary = new MatchSummary(oldest.minusHours(1), newest.plusHours(1), 1, List.of());
        assertTrue(PageBounds.hasBefore(ChatQuery.Sort.DESCENDING, rows, summary));
        assertTrue(PageBounds.hasAfter(ChatQuery.Sort.DESCENDING, false, rows, summary));
    }

    @Test
    void skippedMatchesCountAsRowsBeforeThePageEvenAtTheSameTimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        List<DisplayRow> rows = List.of(row(time, 40), row(time, 41));
        MatchSummary summary = new MatchSummary(time, time, 80, List.of());
        assertFalse(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows, summary));
        assertTrue(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows, summary, 40));
        assertFalse(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows, summary, 0));
        assertFalse(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows, summary, -1));
    }

    @Test
    void jumpPagesThatDoNotFillTheViewNeedAnExtraFetchWhenOlderRowsExist() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        List<DisplayRow> few = List.of(row(time, 0), row(time.plusSeconds(1), 1));
        assertTrue(PageBounds.needsMoreToFill(few, 0, 200, true));
        assertFalse(PageBounds.needsMoreToFill(few, 0, 200, false));
        List<DisplayRow> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(row(time.plusSeconds(i), i));
        }
        assertFalse(PageBounds.needsMoreToFill(many, 0, 200, true));
        assertEquals(24, PageBounds.extraFillLimit(200, 8));
        assertEquals(32, PageBounds.extraFillLimit(200, 32));
    }

    @Test
    void scrubberDoesNotTreatAPreviewSliceAsTheWholeDay() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        MatchDay day = new MatchDay(time.toLocalDate(), time, time.plusHours(8), 80);
        assertFalse(PageBounds.canScrollDayLocally(day, 32, -1, false, false));
        assertTrue(PageBounds.canScrollDayLocally(day, 32, -1, true, false));
        assertTrue(PageBounds.canScrollDayLocally(day, 32, -1, false, true));
        assertTrue(PageBounds.canScrollDayLocally(day, 80, -1, false, false));
        MatchDay collapsed = new MatchDay(time.toLocalDate(), time, time, 50);
        assertFalse(PageBounds.canScrollDayLocally(collapsed, 32, 10, false, true));
        assertTrue(PageBounds.canScrollDayLocally(collapsed, 50, 10, false, false));
    }

    @Test
    void unwrapsCompletionExceptionsForLogMessages() {
        LogDataException storeError = new LogDataException("unsupported regex: negative lookahead is not supported by RE2");
        Throwable wrapped = new CompletionException(storeError);
        assertSame(storeError, LogBrowserQueries.unwrap(wrapped));
        assertEquals(storeError.getMessage(), LogBrowserQueries.unwrap(wrapped).getMessage());
    }

    @Test
    void preferLaterPicksTheCloserTimestampAndBreaksTiesTowardLater() {
        LocalDateTime target = LocalDateTime.of(2026, 8, 27, 12, 0, 0);
        assertTrue(PageBounds.preferLater(target, target.plusMinutes(1), target.minusHours(1)));
        assertFalse(PageBounds.preferLater(target, target.plusHours(1), target.minusMinutes(1)));
        assertTrue(PageBounds.preferLater(target, target.plusMinutes(5), target.minusMinutes(5)));
        assertTrue(PageBounds.preferLater(target, target, target.minusSeconds(1)));
        assertFalse(PageBounds.preferLater(target, null, target.minusMinutes(1)));
        assertTrue(PageBounds.preferLater(target, target.plusMinutes(1), null));
    }

    @Test
    void aTailViewportReloadsSoReopenShowsMessagesThatArrivedLater() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        ListSnapshot tail = new ListSnapshot(List.of(row(time, 0)), true, false, 400, true, 1, false, 12);
        ListSnapshot middle = new ListSnapshot(List.of(row(time, 0)), true, false, 40, false, 1, false, 12);
        assertFalse(LogBrowserQueries.restoreSavedViewport(tail));
        assertTrue(LogBrowserQueries.restoreSavedViewport(middle));
        assertFalse(LogBrowserQueries.restoreSavedViewport(ListSnapshot.EMPTY));
        assertFalse(LogBrowserQueries.restoreSavedViewport(null));
        try {
            LogBrowserQueries.rememberSession(tail, MatchSummary.empty());
            LogBrowserQueries reopenedAtTail = new LogBrowserQueries();
            reopenedAtTail.restoreSessionLocation();
            assertTrue(reopenedAtTail.consumeReload());

            LogBrowserQueries.rememberSession(middle, MatchSummary.empty());
            LogBrowserQueries reopenedInPlace = new LogBrowserQueries();
            reopenedInPlace.restoreSessionLocation();
            assertFalse(reopenedInPlace.consumeReload());
        } finally {
            LogBrowserQueries.rememberSession(ListSnapshot.EMPTY, MatchSummary.empty());
        }
    }

    @Test
    void keepViewportWhenTheListAlreadyHasAPlace() {
        assertFalse(LogBrowserQueries.keepViewport(null, true));
        assertFalse(LogBrowserQueries.keepViewport(LocalDateTime.of(2026, 8, 27, 10, 0), false));
        assertTrue(LogBrowserQueries.keepViewport(LocalDateTime.of(2026, 8, 27, 10, 0), true));
    }

    @Test
    void mergingBothJumpSidesKeepsOlderAndNewerRows() {
        LocalDateTime stay = LocalDateTime.of(2026, 8, 27, 12, 0, 0);
        List<DisplayRow> earlier = List.of(row(stay.minusHours(1), 0), row(stay, 1));
        List<DisplayRow> later = List.of(row(stay, 1), row(stay.plusHours(1), 2));
        List<DisplayRow> merged = LogBrowserQueries.mergeClosestSides(earlier, later, ChatQuery.Sort.ASCENDING);
        assertEquals(3, merged.size());
        assertEquals(stay.minusHours(1), merged.getFirst().entry().timestamp());
        assertEquals(stay.plusHours(1), merged.getLast().entry().timestamp());
    }

    @Test
    void aFullEarlierPageUnlocksScrollingUpEvenWhenTheSummaryIsStale() {
        LocalDateTime stay = LocalDateTime.of(2026, 8, 27, 12, 0, 0);
        List<DisplayRow> rows = List.of(row(stay, 0), row(stay.plusMinutes(1), 1));
        MatchSummary searchHits = new MatchSummary(stay, stay.plusMinutes(1), 2, List.of());
        assertFalse(PageBounds.hasBefore(ChatQuery.Sort.ASCENDING, rows, searchHits));
        assertTrue(LogBrowserQueries.hasMoreBefore(true, ChatQuery.Sort.ASCENDING, rows, searchHits));
        assertFalse(LogBrowserQueries.hasMoreBefore(false, ChatQuery.Sort.ASCENDING, rows, searchHits));
        assertTrue(LogBrowserQueries.hasMoreAfter(true, ChatQuery.Sort.ASCENDING, rows, searchHits));
    }
}
