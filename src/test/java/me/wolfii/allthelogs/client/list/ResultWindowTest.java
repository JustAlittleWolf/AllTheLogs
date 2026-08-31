package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResultWindowTest {
    private static DisplayRow row(String file, int line) {
        return rowAt(LocalDateTime.of(2026, 8, 26, 10, 0).plusSeconds(line), line, file);
    }

    private static DisplayRow rowAt(LocalDateTime time, int line) {
        return rowAt(time, line, "a.log");
    }

    private static DisplayRow rowAt(LocalDateTime time, int line, String file) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of(file)), time.toLocalDate(), "26.2", time, time);
        ChatEntry entry = new ChatEntry(log, time, line, "msg-" + line);
        return new DisplayRow(entry, true, List.of());
    }

    @Test
    void replacingThePageKeepsTheAnchorRowAtTheSameScreenPosition() {
        double scrollY = 10;
        int rowHeight = 12;
        double newScroll = ResultWindow.keepAnchor(2, 0, 2 * rowHeight, 0, scrollY, 0, 0);
        assertEquals(2 * rowHeight - scrollY, 0 * rowHeight - newScroll, 0.001);
        assertEquals(0, ResultWindow.keepAnchor(0, 4, 0, 80, 0, 80, 0), 0.001);
        assertEquals(320, ResultWindow.keepAnchor(0, 4, 0, 400, 0, 80, 0), 0.001);
    }

    @Test
    void trimKeepsTheVisibleMatchesWhenTheBufferGrowsPastTheLimit() {
        List<DisplayRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(row("a.log", i));
        }
        // Visible rows 6-8, limit 4 matches → keep a window covering 6-8.
        List<DisplayRow> trimmed = DisplayRows.trimToMatchLimit(rows, 4, 6, 8);
        assertEquals(List.of(5, 6, 7, 8), trimmed.stream().map(DisplayRow::lineIndex).toList());
        List<DisplayRow> merged = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            merged.add(row("a.log", i));
        }
        assertTrue(DisplayRows.trimmedHead(merged, trimmed));
        assertTrue(DisplayRows.trimmedTail(merged, trimmed));
    }

    @Test
    void matchCountAtCountsHitsThatShareATimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        List<DisplayRow> rows = List.of(rowAt(time, 0), rowAt(time, 1), rowAt(time.plusSeconds(1), 2));
        assertEquals(2, DisplayRows.matchCountAt(rows, time));
        assertEquals(0, DisplayRows.matchCountAt(rows, time.plusHours(1)));
    }

    @Test
    void rowKeyIgnoresSessionEndTimeSoLaterCapturesDoNotLookNew() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog first = new ChatLog(new LogSource.Session("live"), start.toLocalDate(), "26.2", start, start);
        ChatLog later = new ChatLog(new LogSource.Session("live"), start.toLocalDate(), "26.2", start,
            start.plusMinutes(5));
        DisplayRow before = new DisplayRow(new ChatEntry(first, start, 0, "a"), true, List.of());
        DisplayRow after = new DisplayRow(new ChatEntry(later, start, 0, "a"), true, List.of());
        assertEquals(before.key(), after.key());
        assertEquals(0, DisplayRows.countNewKeys(List.of(after), DisplayRows.keysOf(List.of(before))));
        assertEquals(1, DisplayRows.mergeUnique(List.of(before), List.of(after)).size());
    }

    @Test
    void matchCountCountsOnlyHits() {
        List<DisplayRow> rows = List.of(row("a.log", 0), row("a.log", 1));
        assertEquals(2, DisplayRows.matchCount(rows));
        ResultWindow window = new ResultWindow();
        window.reset(rows, false, false);
        assertEquals(2, window.matchCount());
    }

    @Test
    void matchCountOnDateIgnoresOtherDaysAndContextLines() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 26, 10, 0);
        LocalDateTime nextDay = LocalDateTime.of(2026, 8, 27, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), first.toLocalDate(), "26.2", first, first);
        DisplayRow context = new DisplayRow(new ChatEntry(log, first, 0, "ctx"), false, List.of());
        List<DisplayRow> rows = List.of(context, rowAt(first, 1), rowAt(nextDay, 2));
        assertEquals(1, DisplayRows.matchCountOnDate(rows, first.toLocalDate()));
        assertEquals(1, DisplayRows.matchCountOnDate(rows, nextDay.toLocalDate()));
        assertEquals(0, DisplayRows.matchCountOnDate(rows, first.toLocalDate().minusDays(1)));
    }

    @Test
    void coversTimeAndNearestIndexUseBufferedTimestamps() {
        ResultWindow window = new ResultWindow();
        window.reset(List.of(row("a.log", 0), row("a.log", 10)), false, false);
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        assertTrue(window.coversTime(start.plusSeconds(10)));
        assertFalse(window.coversTime(start.plusHours(3)));
        assertEquals(1, window.nearestIndex(start.plusSeconds(9)));
        assertTrue(window.showsDate(start.plusSeconds(10)));
        assertFalse(window.showsDate(start.plusDays(1)));
    }

    @Test
    void showsDateDoesNotTreatTheEmptyGapBetweenBufferedDaysAsLoaded() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 26, 10, 0);
        LocalDateTime last = LocalDateTime.of(2026, 8, 28, 10, 0);
        ResultWindow window = new ResultWindow();
        window.reset(List.of(rowAt(first, 0), rowAt(last, 1)), false, false);
        LocalDateTime gap = LocalDateTime.of(2026, 8, 27, 12, 0);
        assertTrue(window.coversTime(gap));
        assertFalse(window.showsDate(gap));
        assertTrue(window.showsDate(first.plusHours(1)));
        assertTrue(window.showsDate(last.minusHours(1)));
    }

    @Test
    void firstAndLastMatchSkipContextLinesThenFallBackToTheRow() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow context = new DisplayRow(new ChatEntry(log, time, 0, "ctx"), false, List.of());
        DisplayRow first = rowAt(time, 1);
        DisplayRow last = rowAt(time.plusSeconds(1), 2);
        List<DisplayRow> rows = List.of(context, first, last);
        assertEquals(first, DisplayRows.firstMatch(rows));
        assertEquals(last, DisplayRows.lastMatch(rows));
        assertEquals(time, DisplayRows.firstMatchTime(rows));
        assertEquals(time.plusSeconds(1), DisplayRows.lastMatchTime(rows));
        List<DisplayRow> onlyContext = List.of(context);
        assertEquals(context, DisplayRows.firstMatch(onlyContext));
        assertEquals(context, DisplayRows.lastMatch(onlyContext));
    }

    @Test
    void reverseRestoresChronologicalOrderAfterABackwardFetch() {
        List<DisplayRow> newestFirst = List.of(row("a.log", 5), row("a.log", 4), row("a.log", 3));
        List<DisplayRow> chronological = DisplayRows.reversed(newestFirst);
        assertEquals(List.of(3, 4, 5), chronological.stream().map(DisplayRow::lineIndex).toList());
    }

    @Test
    void mergeSortedPutsTheHigherLineIndexLaterWhenTimestampsMatch() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        DisplayRow first = rowAt(time, 1);
        DisplayRow second = rowAt(time, 4);
        List<DisplayRow> merged = DisplayRows.mergeSorted(List.of(second), List.of(first),
            me.wolfii.allthelogs.api.ChatQuery.Sort.ASCENDING);
        assertEquals(List.of(1, 4), merged.stream().map(DisplayRow::lineIndex).toList());
    }
}
