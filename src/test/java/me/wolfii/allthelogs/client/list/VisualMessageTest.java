package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualMessageTest {
    @Test
    void trimsEachLineAndTurnsLiteralEscapesIntoBreaks() {
        assertEquals("hello\\n\nworld", VisualMessage.visual("  hello \\n world  ", true));
        assertEquals("hello \\n world", VisualMessage.visual("  hello \\n world  ", false));
        assertEquals("a\nb\\n\nc", VisualMessage.visual(" a \nb\\n c ", true));
    }

    @Test
    void sessionLogsKeepLiteralEscapes() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
        ChatLog session = new ChatLog(new LogSource.Session("id"), LocalDate.of(2026, 8, 27), "26.2", time, time);
        assertFalse(VisualMessage.interpretEscapes(session));
        ChatLog file = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 27), "26.2", time, time);
        assertTrue(VisualMessage.interpretEscapes(file));
    }

    @Test
    void escapeCharsCoverTheVisibleTokenBeforeTheBreak() {
        String visual = VisualMessage.visual("hello\\nworld", true);
        assertEquals("hello\\n\nworld", visual);
        assertTrue(VisualMessage.escapeChar(visual, 5, true));
        assertTrue(VisualMessage.escapeChar(visual, 6, true));
        assertFalse(VisualMessage.escapeChar(visual, 7, true));
        assertFalse(VisualMessage.escapeChar(visual, 0, true));
        assertFalse(VisualMessage.escapeChar(visual, 5, false));
    }

    @Test
    void remapsStoredFormattingOntoTrimmedVisualText() {
        int red = PackedFormatting.color(0xFF5555);
        long[] stored = {PackedFormatting.run(2, 5, red)};
        long[] visual = VisualMessage.remapFormatting("  hello  ", stored, false);
        assertEquals("hello", VisualMessage.visual("  hello  ", false));
        assertEquals(red, PackedFormatting.at(visual, 0));
        assertEquals(red, PackedFormatting.at(visual, 4));
    }

    @Test
    void trimsLeadingAndTrailingNewlinesForDisplay() {
        assertEquals("hello", VisualMessage.visual("\nhello\n", false));
        assertEquals("hello\\n\nworld", VisualMessage.visual("\nhello\\nworld\n", true));
        assertEquals("hello", VisualMessage.trimNewlines("\n\nhello\n"));
    }
}
