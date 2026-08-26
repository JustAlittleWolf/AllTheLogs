package me.wolfii.allthelogs.client.view;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSelectionTest {
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

    private static DisplayRow row(String message) {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 26), "26.2", time, time);
        return new DisplayRow(new ChatEntry(log, time, 0, message), true, Duration.ZERO, List.of());
    }
}
