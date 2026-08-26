package me.wolfii.allthelogs.client.view;

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
}
