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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSelectionTest {
    private static DisplayRow row(String message) {
        return rowOn(LocalDateTime.of(2026, 8, 26, 10, 0), 0, message);
    }

    private static DisplayRow rowOn(LocalDateTime time, String message) {
        return rowOn(time, 0, message);
    }

    private static DisplayRow rowOn(LocalDateTime time, int line, String message) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        return new DisplayRow(new ChatEntry(log, time, line, message), true, List.of());
    }

    @Test
    void copiesMessageTextWithoutCrossingRowsWhenTheRangeIsOnOneLine() {
        MessageSelection selection = new MessageSelection();
        selection.start(0, 1);
        selection.extend(0, 4);
        assertEquals("ell", selection.copy(List.of(row("hello"), row("world"))));
        assertTrue(selection.covers(0, 1));
        assertTrue(!selection.covers(0, 4));
    }

    @Test
    void copiesAcrossRows() {
        MessageSelection selection = new MessageSelection();
        selection.start(0, 3);
        selection.extend(1, 2);
        assertEquals("lo\nwo", selection.copy(List.of(row("hello"), row("world"))));
    }

    @Test
    void selectDateCoversEveryLoadedRowOnThatDay() {
        DisplayRow earlier = rowOn(LocalDateTime.of(2026, 8, 26, 10, 0), "one");
        DisplayRow sameDay = rowOn(LocalDateTime.of(2026, 8, 26, 23, 0), "two");
        DisplayRow nextDay = rowOn(LocalDateTime.of(2026, 8, 27, 1, 0), "three");
        MessageSelection selection = new MessageSelection();
        selection.selectDate(List.of(earlier, sameDay, nextDay), LocalDate.of(2026, 8, 26));
        assertEquals("one\ntwo", selection.copy(List.of(earlier, sameDay, nextDay)));
        assertTrue(selection.covers(0, 0));
        assertTrue(selection.covers(1, 0));
        assertTrue(!selection.covers(2, 0));
    }

    @Test
    void selectRowCoversTheWholeMessage() {
        DisplayRow row = row("hello");
        MessageSelection selection = new MessageSelection();
        selection.selectRow(0, row.message().length());
        assertEquals("hello", selection.copy(List.of(row)));
        assertTrue(selection.covers(0, 0));
        assertTrue(selection.covers(0, 4));
        assertTrue(!selection.covers(0, 5));
    }

    @Test
    void wordRangeSelectsLettersThenPunctuationThenWhitespace() {
        assertEquals(0, MessageSelection.wordRange("hello-world", 1)[0]);
        assertEquals(5, MessageSelection.wordRange("hello-world", 1)[1]);
        assertEquals(5, MessageSelection.wordRange("hello-world", 5)[0]);
        assertEquals(6, MessageSelection.wordRange("hello-world", 5)[1]);
        assertEquals(6, MessageSelection.wordRange("hello-world", 8)[0]);
        assertEquals(11, MessageSelection.wordRange("hello-world", 8)[1]);
        assertEquals(2, MessageSelection.wordRange("ab  cd", 3)[0]);
        assertEquals(4, MessageSelection.wordRange("ab  cd", 3)[1]);
        DisplayRow row = row("hello world");
        MessageSelection selection = new MessageSelection();
        selection.selectWord(0, row.message(), 1);
        assertEquals("hello", selection.copy(List.of(row)));
        selection.selectWord(0, row.message(), 8);
        assertEquals("world", selection.copy(List.of(row)));
    }

    @Test
    void retainInRemapsIndexesAndClearsWhenASelectedRowUnloads() {
        DisplayRow first = rowOn(LocalDateTime.of(2026, 8, 26, 10, 0), 0, "one");
        DisplayRow second = rowOn(LocalDateTime.of(2026, 8, 26, 11, 0), 1, "two");
        DisplayRow third = rowOn(LocalDateTime.of(2026, 8, 26, 12, 0), 2, "three");
        MessageSelection selection = new MessageSelection();
        selection.start(1, 1);
        selection.extend(1, 3);
        selection.retainIn(List.of(first, second, third), List.of(second, third));
        assertEquals("wo", selection.copy(List.of(second, third)));
        selection.retainIn(List.of(second, third), List.of(third));
        assertTrue(selection.isEmpty());
    }
}
