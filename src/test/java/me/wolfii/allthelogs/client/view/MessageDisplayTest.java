package me.wolfii.allthelogs.client.view;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageDisplayTest {
    @Test
    void trimsEachLineAndTurnsLiteralEscapesIntoBreaks() {
        assertEquals("hello\\n\nworld", MessageDisplay.visual("  hello \\n world  ", true));
        assertEquals("hello \\n world", MessageDisplay.visual("  hello \\n world  ", false));
        assertEquals("a\nb\\n\nc", MessageDisplay.visual(" a \nb\\n c ", true));
    }

    @Test
    void sessionLogsKeepLiteralEscapes() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
        ChatLog session = new ChatLog(new LogSource.Session("id"), LocalDate.of(2026, 8, 27), "26.2", time, time);
        assertFalse(MessageDisplay.interpretEscapes(session));
        ChatLog file = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 27), "26.2", time, time);
        assertTrue(MessageDisplay.interpretEscapes(file));
    }

    @Test
    void escapeCharsCoverTheVisibleTokenBeforeTheBreak() {
        String visual = MessageDisplay.visual("hello\\nworld", true);
        assertEquals("hello\\n\nworld", visual);
        assertTrue(MessageDisplay.escapeChar(visual, 5, true));
        assertTrue(MessageDisplay.escapeChar(visual, 6, true));
        assertFalse(MessageDisplay.escapeChar(visual, 7, true));
        assertFalse(MessageDisplay.escapeChar(visual, 0, true));
        assertFalse(MessageDisplay.escapeChar(visual, 5, false));
    }
}
