package me.wolfii.allthelogs.client.list;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageWrapTest {
    @Test
    void wrapsOnSpacesThenHardBreaksOversizedTokens() {
        assertEquals(List.of("hello world"), MessageWrap.lines("hello world", 20, String::length));
        assertEquals(List.of("hello ", "world"), MessageWrap.lines("hello world", 6, String::length));
        assertEquals(List.of("abcd", "ef"), MessageWrap.lines("abcdef", 4, String::length));
        assertEquals(List.of(""), MessageWrap.lines("", 4, String::length));
        assertEquals(1, MessageWrap.lineCount("short", 40, String::length));
        assertEquals(2, MessageWrap.lineCount("hello world", 6, String::length));
    }

    @Test
    void indexAtXUsesPrefixWidths() {
        assertEquals(0, MessageWrap.indexAtX("abcd", 0, String::length));
        assertEquals(2, MessageWrap.indexAtX("abcd", 2, String::length));
        assertEquals(4, MessageWrap.indexAtX("abcd", 10, String::length));
        assertEquals(8, MessageWrap.charIndex("hello world", 6, 1, 2, String::length));
    }

    @Test
    void hardNewlinesBecomeVisualRowsAndKeepSourceIndexes() {
        assertEquals(List.of("", "KING", "next"),
            MessageWrap.lines("\nKING\nnext", Integer.MAX_VALUE, String::length));
        List<MessageWrap.Line> wrapped = MessageWrap.wrap("hello\n\nworld", Integer.MAX_VALUE, String::length);
        assertEquals(3, wrapped.size());
        assertEquals("hello", wrapped.get(0).text());
        assertEquals(0, wrapped.get(0).start());
        assertEquals("", wrapped.get(1).text());
        assertEquals(6, wrapped.get(1).start());
        assertEquals("world", wrapped.get(2).text());
        assertEquals(7, wrapped.get(2).start());
        assertEquals(7, MessageWrap.charIndex("hello\n\nworld", 40, 2, 0, String::length));
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
        assertEquals(List.of("bold", "plain"), MessageWrap.lines("boldplain", 8, boldFirst));
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
        assertEquals(List.of("ab", "cde"), MessageWrap.lines("abcde", 4, widths));
        assertEquals(5, calls[0]);
    }
}
