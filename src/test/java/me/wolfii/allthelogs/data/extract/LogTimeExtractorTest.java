package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

class LogTimeExtractorTest {
    private static LogTimeExtractor.Stamp stamp(String line) {
        Matcher matcher = LogTimeExtractor.LINE_START.matcher(line);
        assertTrue(matcher.find(), line);
        return LogTimeExtractor.parse(matcher);
    }

    @Test
    void timeOnlyLinesHaveANullDate() {
        LogTimeExtractor.Stamp stamp = stamp("[12:16:21] [Client thread/INFO]: [CHAT] hello");
        assertNull(stamp.date());
        assertEquals(LocalTime.of(12, 16, 21), stamp.time());
    }

    @Test
    void isoDatesAreKept() {
        LogTimeExtractor.Stamp stamp = stamp("[2026-08-25 21:04:09] [Render thread/INFO]: [CHAT] hi");
        assertEquals(LocalDate.of(2026, 8, 25), stamp.date());
        assertEquals(LocalTime.of(21, 4, 9), stamp.time());
    }

    @Test
    void dayMonthYearDatesAreKept() {
        LogTimeExtractor.Stamp stamp = stamp(
            "[24Feb2026 16:08:07.204] [Render thread/INFO]: Connecting to 185.206.150.34, 25588");
        assertEquals(LocalDate.of(2026, 2, 24), stamp.date());
        assertEquals(LocalTime.of(16, 8, 7), stamp.time());
    }
}
