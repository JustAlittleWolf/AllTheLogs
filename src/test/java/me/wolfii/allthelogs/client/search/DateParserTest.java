package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DateParserTest {
    @Test
    void dateParserAcceptsDateAndDateTime() {
        assertEquals(LocalDateTime.of(2026, 8, 26, 0, 0), DateParser.parse("2026-08-26").orElseThrow());
        assertEquals(LocalDateTime.of(2026, 8, 26, 14, 30), DateParser.parse("2026-08-26 14:30").orElseThrow());
        assertTrue(DateParser.parse("not a date").isEmpty());
        assertTrue(DateParser.isBlankOrValid(""));
        assertFalse(DateParser.isBlankOrValid("nope"));
    }

    @Test
    void parseUntilIncludesTheWholeTypedDate() {
        assertEquals(LocalDateTime.of(2026, 8, 27, 0, 0), DateParser.parseUntil("2026-08-26").orElseThrow());
        assertEquals(LocalDateTime.of(2026, 8, 26, 14, 30), DateParser.parseUntil("2026-08-26 14:30").orElseThrow());
        assertTrue(DateParser.parseUntil("").isEmpty());
        assertTrue(DateParser.parseUntil("not a date").isEmpty());
    }

    @Test
    void formatUntilShowsTheInclusiveDateForExclusiveMidnight() {
        assertEquals("2026-08-26", DateParser.formatUntil(LocalDateTime.of(2026, 8, 27, 0, 0)));
        assertEquals("2026-08-26 14:30", DateParser.formatUntil(LocalDateTime.of(2026, 8, 26, 14, 30)));
        assertEquals("", DateParser.formatUntil(null));
        assertEquals("2026-08-26 00:00", DateParser.format(LocalDateTime.of(2026, 8, 26, 0, 0)));
    }
}
