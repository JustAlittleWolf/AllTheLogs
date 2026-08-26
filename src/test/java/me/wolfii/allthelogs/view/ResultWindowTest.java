package me.wolfii.allthelogs.view;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultWindowTest {
    @Test
    void replacingThePageKeepsTheAnchorRowAtTheSameScreenPosition() {
        ResultWindow window = new ResultWindow();
        List<DisplayRow> first = List.of(row("a.log", 0), row("a.log", 1), row("a.log", 2), row("a.log", 3));
        window.reset(first, false, true);

        DisplayRow.RowKey anchor = first.get(2).key();
        double scrollY = 10;
        int rowHeight = 12;
        double screenY = 2 * rowHeight - scrollY;

        List<DisplayRow> next = List.of(row("a.log", 2), row("a.log", 3), row("a.log", 4), row("a.log", 5));
        double newScroll = window.replaceKeepingAnchor(next, true, true, anchor, scrollY, rowHeight);

        assertEquals(screenY, 0 * rowHeight - newScroll, 0.001);
        assertEquals(4, window.rows().size());
        assertEquals(2, window.rows().getFirst().lineIndex());
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
    }

    @Test
    void reverseRestoresChronologicalOrderAfterABackwardFetch() {
        List<DisplayRow> newestFirst = List.of(row("a.log", 5), row("a.log", 4), row("a.log", 3));
        List<DisplayRow> chronological = ResultWindow.reversed(newestFirst);
        assertEquals(List.of(3, 4, 5), chronological.stream().map(DisplayRow::lineIndex).toList());
    }

    private static DisplayRow row(String file, int line) {
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of(file)), LocalDate.of(2026, 8, 26), "26.2", start, start);
        ChatEntry entry = new ChatEntry(log, start.plusSeconds(line), line, "msg-" + line);
        return new DisplayRow(entry, true, Duration.ZERO, List.of());
    }
}
