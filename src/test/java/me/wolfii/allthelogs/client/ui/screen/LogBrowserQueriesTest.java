package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.ChatQuery;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.MatchSummary;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogBrowserQueriesTest {
    @Test
    void exclusiveOffsetIncludesTheTargetTimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        assertEquals(time.plusNanos(1), LogBrowserQueries.exclusiveOffset(time, ChatQuery.Sort.DESCENDING));
        assertEquals(time.minusNanos(1), LogBrowserQueries.exclusiveOffset(time, ChatQuery.Sort.ASCENDING));
    }

    @Test
    void chronologicalPagesHaveOlderRowsBeforeAndNewerRowsAfter() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        LocalDateTime last = first.plusMinutes(1);
        List<DisplayRow> rows = List.of(row(first, 0), row(last, 1));
        MatchSummary summary = new MatchSummary(first.minusHours(1), last.plusHours(1), 1, List.of());
        assertTrue(LogBrowserQueries.pageHasBefore(ChatQuery.Sort.ASCENDING, rows, summary));
        assertTrue(LogBrowserQueries.pageHasAfter(ChatQuery.Sort.ASCENDING, false, rows, summary));
        assertFalse(LogBrowserQueries.pageHasBefore(ChatQuery.Sort.ASCENDING, rows,
            new MatchSummary(first, last, 1, List.of())));
        assertTrue(LogBrowserQueries.pageHasAfter(ChatQuery.Sort.ASCENDING, true, rows,
            new MatchSummary(first, last, 1, List.of())));
    }

    @Test
    void newestFirstPagesHaveNewerRowsBeforeAndOlderRowsAfter() {
        LocalDateTime newest = LocalDateTime.of(2026, 8, 27, 12, 0, 0);
        LocalDateTime oldest = newest.minusMinutes(1);
        List<DisplayRow> rows = List.of(row(newest, 1), row(oldest, 0));
        MatchSummary summary = new MatchSummary(oldest.minusHours(1), newest.plusHours(1), 1, List.of());
        assertTrue(LogBrowserQueries.pageHasBefore(ChatQuery.Sort.DESCENDING, rows, summary));
        assertTrue(LogBrowserQueries.pageHasAfter(ChatQuery.Sort.DESCENDING, false, rows, summary));
    }

    private static DisplayRow row(LocalDateTime time, int line) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        return new DisplayRow(new ChatEntry(log, time, line, "msg"), true, Duration.ZERO, List.of());
    }
}
