package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateParserTest {
    @Test
    void dateParserAcceptsDateAndDateTime() {
        assertEquals(LocalDateTime.of(2026, 8, 26, 0, 0), DateParser.parse("2026-08-26").orElseThrow());
        assertEquals(LocalDateTime.of(2026, 8, 26, 14, 30), DateParser.parse("2026-08-26 14:30").orElseThrow());
        assertTrue(DateParser.parse("not a date").isEmpty());
        assertTrue(DateParser.isBlankOrValid(""));
        assertFalse(DateParser.isBlankOrValid("nope"));
    }
}
