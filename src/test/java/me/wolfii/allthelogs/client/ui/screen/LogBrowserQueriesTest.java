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
    void continueFromIncludesTheCursorSecondAndSkipsAlreadyBufferedHits() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        ChatQuery next = PageBounds.continueFrom(ChatQuery.all().withLimit(100), time, 40);
        assertEquals(time.minusNanos(1), next.offset());
        assertEquals(40, next.skip());
        ChatQuery previous = PageBounds.continueFrom(
            ChatQuery.all().withSort(ChatQuery.Sort.DESCENDING).withLimit(100), time, 40);
        assertEquals(time.plusNanos(1), previous.offset());
        assertEquals(40, previous.skip());
    }

    @Test
    void exclusiveOffsetIncludesTheTargetTimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        assertEquals(time.plusNanos(1), PageBounds.exclusiveOffset(time, ChatQuery.Sort.DESCENDING));
        assertEquals(time.minusNanos(1), PageBounds.exclusiveOffset(time, ChatQuery.Sort.ASCENDING));
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
        assertFalse(PageBounds.canScrollDayLocally(day, 32, -1, false, false, false));
        assertTrue(PageBounds.canScrollDayLocally(day, 32, -1, true, false, false));
        assertTrue(PageBounds.canScrollDayLocally(day, 32, -1, false, true, false));
        assertTrue(PageBounds.canScrollDayLocally(day, 80, -1, false, false, false));
        assertFalse(PageBounds.canScrollDayLocally(day, 32, -1, false, true, true));
        assertFalse(PageBounds.canScrollDayLocally(day, 32, -1, true, false, true));
        assertTrue(PageBounds.canScrollDayLocally(day, 80, -1, false, false, true));
        MatchDay collapsed = new MatchDay(time.toLocalDate(), time, time, 50);
        assertFalse(PageBounds.canScrollDayLocally(collapsed, 32, 10, false, true, false));
        assertTrue(PageBounds.canScrollDayLocally(collapsed, 50, 10, false, false, false));
        assertFalse(PageBounds.canScrollDayLocally(collapsed, 32, 10, false, true, true));
    }

    @Test
    void unwrapsCompletionExceptionsForLogMessages() {
        LogDataException storeError = new LogDataException("unsupported regex: negative lookahead is not supported by RE2");
        Throwable wrapped = new CompletionException(storeError);
        assertSame(storeError, LogBrowserQueries.unwrap(wrapped));
        assertEquals(storeError.getMessage(), LogBrowserQueries.unwrap(wrapped).getMessage());
    }
}
