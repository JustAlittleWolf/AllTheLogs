package me.wolfii.allthelogs.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateParsersTest {
    @Test
    void dateParserAcceptsDateAndDateTime() {
        assertEquals(LocalDateTime.of(2026, 8, 26, 0, 0), DateParsers.parse("2026-08-26").orElseThrow());
        assertEquals(LocalDateTime.of(2026, 8, 26, 14, 30), DateParsers.parse("2026-08-26 14:30").orElseThrow());
        assertTrue(DateParsers.parse("not a date").isEmpty());
        assertTrue(DateParsers.isBlankOrValid(""));
        assertFalse(DateParsers.isBlankOrValid("nope"));
    }
}
