package me.wolfii.allthelogs.client.list;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageWrapTest {
    private static MessageWrap.RangeWidth chars(String text) {
        return MessageWrap.substringWidths(text, String::length);
    }

    private static List<String> lines(String text, int maxWidth) {
        return lines(text, maxWidth, chars(text));
    }

    private static List<String> lines(String text, int maxWidth, MessageWrap.RangeWidth widthOf) {
        return MessageWrap.wrap(text, maxWidth, widthOf).stream().map(MessageWrap.Line::text).toList();
    }

    private static int lineCount(String text, int maxWidth) {
        return MessageWrap.lineCount(text, maxWidth, chars(text));
    }

    @Test
    void wrapsOnSpacesThenHardBreaksOversizedTokens() {
        assertEquals(List.of("hello world"), lines("hello world", 20));
        assertEquals(List.of("hello ", "world"), lines("hello world", 6));
        assertEquals(List.of("abcd", "ef"), lines("abcdef", 4));
        assertEquals(List.of(""), lines("", 4));
        assertEquals(1, lineCount("short", 40));
        assertEquals(2, lineCount("hello world", 6));
    }

    @Test
    void indexAtXUsesPrefixWidths() {
        assertEquals(0, MessageWrap.indexAtX("abcd", 0, chars("abcd")));
        assertEquals(2, MessageWrap.indexAtX("abcd", 2, chars("abcd")));
        assertEquals(4, MessageWrap.indexAtX("abcd", 10, chars("abcd")));
        assertEquals(8, MessageWrap.charIndex("hello world", 6, 1, 2, chars("hello world")));
    }

    @Test
    void hardNewlinesBecomeVisualRowsAndKeepSourceIndexes() {
        assertEquals(List.of("", "KING", "next"),
            lines("\nKING\nnext", Integer.MAX_VALUE));
        List<MessageWrap.Line> wrapped = MessageWrap.wrap("hello\n\nworld", Integer.MAX_VALUE, chars("hello\n\nworld"));
        assertEquals(3, wrapped.size());
        assertEquals("hello", wrapped.get(0).text());
        assertEquals(0, wrapped.get(0).start());
        assertEquals("", wrapped.get(1).text());
        assertEquals(6, wrapped.get(1).start());
        assertEquals("world", wrapped.get(2).text());
        assertEquals(7, wrapped.get(2).start());
        assertEquals(7, MessageWrap.charIndex("hello\n\nworld", 40, 2, 0, chars("hello\n\nworld")));
    }

    @Test
    void rangeWidthsWrapAndHitTestUsingStyledGlyphWidths() {
        MessageWrap.RangeWidth boldFirst = (from, to) -> {
            int width = 0;
            for (int i = from; i < to; i++) {
                width += i < 4 ? 2 : 1;
            }
            return width;
        };
        assertEquals(List.of("bold", "plain"), lines("boldplain", 8, boldFirst));
        assertEquals(4, MessageWrap.charIndex("boldplain", 8, 0, 7, boldFirst));
        assertEquals(6, MessageWrap.charIndex("boldplain", 8, 1, 2, boldFirst));
    }

    @Test
    void prefixWidthsMeasureEachCharacterOnce() {
        int[] calls = {0};
        MessageWrap.RangeWidth widths = MessageWrap.prefixWidths(5, i -> {
            calls[0]++;
            return i < 2 ? 2 : 1;
        });
        assertEquals(5, calls[0]);
        assertEquals(4, widths.width(0, 2));
        assertEquals(3, widths.width(2, 5));
        assertEquals(0, widths.width(3, 3));
        assertEquals(List.of("ab", "cde"), lines("abcde", 4, widths));
        assertEquals(5, calls[0]);
    }

    @Test
    void prefixWidthsAvoidRescanningEveryPrefixDuringWrap() {
        String text = "obfuscated-magic-text ".repeat(20);
        int[] visits = {0};
        MessageWrap.RangeWidth naive = (from, to) -> {
            for (int i = from; i < to; i++) visits[0]++;
            return to - from;
        };
        MessageWrap.wrap(text, 30, naive);
        int naiveVisits = visits[0];
        visits[0] = 0;
        MessageWrap.RangeWidth cached = MessageWrap.prefixWidths(text.length(), i -> {
            visits[0]++;
            return 1;
        });
        MessageWrap.wrap(text, 30, cached);
        assertEquals(text.length(), visits[0]);
        assertTrue(naiveVisits > visits[0] * 5,
            "naive glyph visits " + naiveVisits + " should dwarf cached " + visits[0]);
    }
}
