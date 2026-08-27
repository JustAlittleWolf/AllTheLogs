package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageListLayoutTest {
    @Test
    void extraContextIsTenLines() {
        assertEquals(10, MessageListLayout.EXPAND_LINES);
        assertEquals(10, MessageListLayout.extraContextLines());
    }

    @Test
    void expandFollowsListDirection() {
        assertTrue(MessageListLayout.expandOlderMessages(true, true));
        assertFalse(MessageListLayout.expandOlderMessages(false, true));
        assertFalse(MessageListLayout.expandOlderMessages(true, false));
        assertTrue(MessageListLayout.expandOlderMessages(false, false));
    }

    @Test
    void bottomPadAlignsShortContentToTheBottom() {
        assertEquals(0, MessageListLayout.bottomPad(400, 200));
        assertEquals(80, MessageListLayout.bottomPad(120, 200));
        assertEquals(0, MessageListLayout.bottomPad(0, 200));
    }

    @Test
    void insertsADateHeaderAndAClusterGapBetweenDistantMatches() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow first = new DisplayRow(new ChatEntry(log, time, 0, "a"), true, List.of());
        DisplayRow later = new DisplayRow(new ChatEntry(log, time.plusHours(1), 20, "b"), true, List.of());
        MessageListLayout layout = MessageListLayout.of(List.of(first, later), 5);

        assertEquals(1, layout.dates().size());
        assertEquals(0, layout.dates().getFirst().y());
        assertEquals(MessageListLayout.DATE_HEIGHT, layout.rowY(0));
        assertEquals(MessageListLayout.DATE_HEIGHT + MessageListLayout.ROW_HEIGHT + MessageListLayout.SEPARATOR_HEIGHT,
            layout.rowY(1));
        assertEquals(1, layout.separators().size());
        MessageListLayout.Separator separator = layout.separators().getFirst();
        assertTrue(separator.expandUp());
        assertTrue(separator.expandDown());
        assertEquals(1, separator.afterRow());
        assertEquals(MessageListLayout.ExpandDirection.UP,
            MessageListLayout.expandAtLocalX(separator, 4, 4));
        assertEquals(MessageListLayout.ExpandDirection.DOWN,
            MessageListLayout.expandAtLocalX(separator, 4, 4 + MessageListLayout.CARET_WIDTH + MessageListLayout.CARET_GAP));
        assertEquals(null, MessageListLayout.expandAtLocalX(separator, 4, 40));
    }

    @Test
    void doesNotGapAdjacentLinesFromTheSameLog() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow a = new DisplayRow(new ChatEntry(log, time, 0, "a"), true, List.of());
        DisplayRow b = new DisplayRow(new ChatEntry(log, time.plusMinutes(1), 1, "b"), true, List.of());
        MessageListLayout layout = MessageListLayout.of(List.of(a, b), 5);
        assertEquals(layout.rowY(0) + MessageListLayout.ROW_HEIGHT, layout.rowY(1));
        assertFalse(MessageListLayout.needsSeparator(a, b));
        assertTrue(MessageListLayout.needsSeparator(a,
            new DisplayRow(new ChatEntry(log, time.plusHours(2), 12, "c"), true, List.of())));
        ChatLog other = new ChatLog(new LogSource.File(Path.of("b.log")), time.toLocalDate(), "26.2", time, time);
        assertTrue(MessageListLayout.needsSeparator(a,
            new DisplayRow(new ChatEntry(other, time.plusMinutes(1), 1, "other"), true, List.of())));
    }

    @Test
    void wrappedLinesTakeARowHeightEach() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "abcdefghij"), true, List.of());
        MessageListLayout layout = MessageListLayout.of(List.of(row), 5, 4, charWidths());
        assertEquals(MessageListLayout.DATE_HEIGHT, layout.rowY(0));
        assertEquals(3 * MessageListLayout.ROW_HEIGHT, layout.rowHeight(0));
        MessageListLayout styled = MessageListLayout.of(List.of(row), 5, 6,
            (displayRow, from, to) -> (to - from) * 2);
        assertEquals(4 * MessageListLayout.ROW_HEIGHT, styled.rowHeight(0));
    }

    @Test
    void hardNewlinesEachTakeARowHeight() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "a\nb\nc"), true, List.of());
        MessageListLayout layout = MessageListLayout.of(List.of(row), 5, 40, charWidths());
        assertEquals(3 * MessageListLayout.ROW_HEIGHT, layout.rowHeight(0));
    }

    @Test
    void stickyHeaderFollowsTheScrolledDate() {
        DisplayRow day1 = row("a.log", 0, LocalDateTime.of(2026, 8, 26, 10, 0));
        DisplayRow day2 = row("b.log", 0, LocalDateTime.of(2026, 8, 27, 10, 0));
        MessageListLayout layout = MessageListLayout.of(List.of(day1, day2), 5);
        assertEquals(LocalDate.of(2026, 8, 26), layout.stickyAt(0).date());
        assertEquals(LocalDate.of(2026, 8, 27), layout.stickyAt(layout.dates().get(1).y()).date());
        assertEquals(MessageListLayout.DATE_HEIGHT + MessageListLayout.ROW_HEIGHT + MessageListLayout.DATE_GAP
                + MessageListLayout.SEPARATOR_HEIGHT,
            layout.dates().get(1).y());
        assertEquals(1, layout.separators().size());
    }

    private static MessageListLayout.RowRangeWidth charWidths() {
        return (row, from, to) -> to - from;
    }

    private static DisplayRow row(String file, int line, LocalDateTime time) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of(file)), time.toLocalDate(), "26.2", time, time);
        ChatEntry entry = new ChatEntry(log, time, line, "msg-" + line);
        return new DisplayRow(entry, true, List.of());
    }
}
