package me.wolfii.allthelogs.runtime;

import me.wolfii.allthelogs.data.ChatQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllTheLogsSettingsAndHelpersTest {
    @Test
    void settingsRoundTripThroughJson(@TempDir Path dir) throws IOException {
        AllTheLogsSettings settings = new AllTheLogsSettings();
        settings.setContextLines(12);
        settings.setLimit(250);
        settings.setCaseSensitive(true);
        settings.setRegex(true);
        settings.setSort(ChatQuery.Sort.DESCENDING);

        Path file = dir.resolve("allthelogs.json");
        settings.save(file);
        AllTheLogsSettings loaded = AllTheLogsSettings.load(file);

        assertEquals(12, loaded.contextLines());
        assertEquals(250, loaded.limit());
        assertTrue(loaded.caseSensitive());
        assertTrue(loaded.regex());
        assertEquals(ChatQuery.Sort.DESCENDING, loaded.sort());
    }

    @Test
    void dateParserAcceptsDateAndDateTime() {
        assertEquals(LocalDateTime.of(2026, 8, 26, 0, 0), DateParsers.parse("2026-08-26").orElseThrow());
        assertEquals(LocalDateTime.of(2026, 8, 26, 14, 30), DateParsers.parse("2026-08-26 14:30").orElseThrow());
        assertTrue(DateParsers.parse("not a date").isEmpty());
        assertTrue(DateParsers.isBlankOrValid(""));
        assertFalse(DateParsers.isBlankOrValid("nope"));
    }

    @Test
    void timelineProgressAndDownsampleKeepTheEdges() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime last = LocalDateTime.of(2026, 1, 2, 0, 0);
        assertEquals(0, TimelineLayout.progress(first, first, last));
        assertEquals(1, TimelineLayout.progress(last, first, last));
        assertEquals(0.5, TimelineLayout.progress(first.plusHours(12), first, last), 0.0001);

        List<LocalDateTime> times = List.of(first, first.plusHours(1), first.plusHours(2), last);
        List<LocalDateTime> sampled = TimelineLayout.downsample(times, 10, 20);
        assertEquals(first, sampled.getFirst());
        assertEquals(last, sampled.getLast());
        assertTrue(sampled.size() <= times.size());
        assertEquals(LocalDate.of(2026, 1, 1), TimelineLayout.ticks(first, last).getFirst().at().toLocalDate());
    }
}
