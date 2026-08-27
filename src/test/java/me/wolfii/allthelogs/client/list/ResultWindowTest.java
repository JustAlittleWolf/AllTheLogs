package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultWindowTest {
    @Test
    void replacingThePageKeepsTheAnchorRowAtTheSameScreenPosition() {
        double scrollY = 10;
        int rowHeight = 12;
        double newScroll = ResultWindow.keepAnchor(2, 0, 2 * rowHeight, 0, scrollY);
        assertEquals(2 * rowHeight - scrollY, 0 * rowHeight - newScroll, 0.001);
    }

    @Test
    void trimKeepsTheVisibleMatchesWhenTheBufferGrowsPastTheLimit() {
        List<DisplayRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(row("a.log", i));
        }
        // Visible rows 6-8, limit 4 matches → keep a window covering 6-8.
        List<DisplayRow> trimmed = ResultWindow.trimToMatchLimit(rows, 4, 6, 8);
        assertEquals(List.of(5, 6, 7, 8), trimmed.stream().map(DisplayRow::lineIndex).toList());
        List<DisplayRow> merged = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            merged.add(row("a.log", i));
        }
        assertTrue(ResultWindow.trimmedHead(merged, trimmed));
        assertTrue(ResultWindow.trimmedTail(merged, trimmed));
    }

    @Test
    void matchCountCountsOnlyHits() {
        List<DisplayRow> rows = List.of(row("a.log", 0), row("a.log", 1));
        assertEquals(2, ResultWindow.matchCount(rows));
        ResultWindow window = new ResultWindow();
        window.reset(rows, false, false);
        assertEquals(2, window.matchCount());
    }

    @Test
    void coversTimeAndNearestIndexUseBufferedTimestamps() {
        ResultWindow window = new ResultWindow();
        window.reset(List.of(row("a.log", 0), row("a.log", 10)), false, false);
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        assertTrue(window.coversTime(start.plusSeconds(10)));
        assertFalse(window.coversTime(start.plusHours(3)));
        assertEquals(1, window.nearestIndex(start.plusSeconds(9)));
    }

    @Test
    void reverseRestoresChronologicalOrderAfterABackwardFetch() {
        List<DisplayRow> newestFirst = List.of(row("a.log", 5), row("a.log", 4), row("a.log", 3));
        List<DisplayRow> chronological = ResultWindow.reversed(newestFirst);
        assertEquals(List.of(3, 4, 5), chronological.stream().map(DisplayRow::lineIndex).toList());
    }

    @Test
    void mergeSortedPutsTheHigherLineIndexLaterWhenTimestampsMatch() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        DisplayRow first = rowAt(time, 1);
        DisplayRow second = rowAt(time, 4);
        List<DisplayRow> merged = ResultWindow.mergeSorted(List.of(second), List.of(first),
            me.wolfii.allthelogs.data.ChatQuery.Sort.ASCENDING);
        assertEquals(List.of(1, 4), merged.stream().map(DisplayRow::lineIndex).toList());
    }

    private static DisplayRow row(String file, int line) {
        return rowAt(LocalDateTime.of(2026, 8, 26, 10, 0).plusSeconds(line), line, file);
    }

    private static DisplayRow rowAt(LocalDateTime time, int line) {
        return rowAt(time, line, "a.log");
    }

    private static DisplayRow rowAt(LocalDateTime time, int line, String file) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of(file)), time.toLocalDate(), "26.2", time, time);
        ChatEntry entry = new ChatEntry(log, time, line, "msg-" + line);
        return new DisplayRow(entry, true, Duration.ZERO, List.of());
    }
}
