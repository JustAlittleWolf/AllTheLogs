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
}
