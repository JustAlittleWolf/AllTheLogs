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

    @Test
    void matchAgreesWithTheRegexOnTypicalPrefixes() {
        String[] lines = {
            "[12:16:21] [Client thread/INFO]: [CHAT] hello",
            "[9:00:00] [Client thread/INFO]: [CHAT] one digit hour",
            "[2026-08-25 21:04:09] [Render thread/INFO]: [CHAT] hi",
            "[2026-08-25T21:04:09] [Render thread/INFO]: [CHAT] hi",
            "[24Feb2026 16:08:07.204] [Render thread/INFO]: Connecting to 185.206.150.34, 25588",
            "[4Feb2026 6:08:07,1] [Render thread/INFO]: [CHAT] hi",
            "[25:00:00] [Client thread/INFO]: [CHAT] invalid clock",
            "not a log line",
            "[12:16:21]no space",
            " [12:16:21] [Client thread/INFO]: indented",
            "[2026-08-25] [Render thread/INFO]: date without time",
            "\t- minecraft 1.20.2"
        };
        for (String line : lines) {
            Matcher matcher = LogTimeExtractor.LINE_START.matcher(line);
            boolean regex = matcher.find();
            LogTimeExtractor.Prefix prefix = LogTimeExtractor.match(line);
            assertEquals(regex, prefix != null, line);
            if (prefix == null) continue;
            assertEquals(matcher.end(), prefix.end(), line);
            LogTimeExtractor.Stamp fromRegex = LogTimeExtractor.parse(matcher);
            LogTimeExtractor.Stamp fromHand = prefix.stamp();
            if (fromRegex == null) {
                assertNull(fromHand, line);
            } else {
                assertEquals(fromRegex, fromHand, line);
            }
        }
    }

    @Test
    void invalidClocksStillMatchThePrefix() {
        String line = "[25:00:00] [Client thread/INFO]: [CHAT] hi";
        LogTimeExtractor.Prefix prefix = LogTimeExtractor.match(line);
        assertNotNull(prefix);
        assertNull(prefix.stamp());
        Matcher matcher = LogTimeExtractor.LINE_START.matcher(line);
        assertTrue(matcher.find());
        assertEquals(matcher.end(), prefix.end());
        assertNull(LogTimeExtractor.parse(matcher));
    }
}
