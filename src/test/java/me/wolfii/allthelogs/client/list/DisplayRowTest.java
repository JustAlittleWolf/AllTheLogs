package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisplayRowTest {
    private static ChatLog log(String name) {
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        return new ChatLog(new LogSource.File(Path.of(name)), LocalDate.of(2026, 8, 26), "26.2", start, start);
    }

    private static ChatEntry entry(ChatLog log, int line, String message) {
        return new ChatEntry(log, log.startTime().plusSeconds(line), line, message);
    }

    @Test
    void withoutASearchEveryRowIsAMatch() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = DisplayRow.from(List.of(entry(log, 0, "one"), entry(log, 1, "two")),
            SearchFilter.defaults());
        assertTrue(rows.get(0).match());
        assertTrue(rows.get(1).match());
        assertTrue(rows.get(0).highlights().isEmpty());
    }

    @Test
    void onlyMatchingRowsAreHitsAndCarryHighlights() {
        ChatLog log = log("a.log");
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 10, 0, 0);
        ChatEntry before = new ChatEntry(log, base, 0, "hello");
        ChatEntry hit = new ChatEntry(log, base.plusMinutes(1), 1, "needle");
        ChatEntry after = new ChatEntry(log, base.plusMinutes(20), 2, "later");

        List<DisplayRow> rows = DisplayRow.from(List.of(before, hit, after),
            SearchFilter.defaults().withText("needle"));

        assertFalse(rows.get(0).match());
        assertTrue(rows.get(1).match());
        assertFalse(rows.get(2).match());
        assertTrue(rows.get(0).highlights().isEmpty());
        assertTrue(rows.get(2).highlights().isEmpty());
        assertEquals(1, rows.get(1).highlights().size());
    }

    @Test
    void cachesVisualTextAndRemappedFormatting() {
        ChatLog log = log("a.log");
        int red = PackedFormatting.color(0xFF5555);
        ChatEntry entry = new ChatEntry(log, log.startTime(), 0, "  hello  ",
            new long[]{PackedFormatting.run(2, 5, red)});
        DisplayRow row = new DisplayRow(entry, true, List.of());
        assertEquals("hello", row.message());
        assertSame(row.message(), row.message());
        assertEquals(red, PackedFormatting.at(row.visualFormatting(), 0));
        assertEquals(red, PackedFormatting.at(row.visualFormatting(), 4));
    }
}
