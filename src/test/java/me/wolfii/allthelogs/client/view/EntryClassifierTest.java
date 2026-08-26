package me.wolfii.allthelogs.client.view;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryClassifierTest {
    @Test
    void withoutASearchEveryRowIsAMatch() {
        ChatLog log = log("a.log");
        List<DisplayRow> rows = EntryClassifier.classify(List.of(entry(log, 0, "one"), entry(log, 1, "two")),
            SearchFilter.defaults());
        assertTrue(rows.get(0).match());
        assertTrue(rows.get(1).match());
        assertTrue(rows.get(0).highlights().isEmpty());
    }

    @Test
    void contextRowsAreGreyedByTimeFromTheNearestHit() {
        ChatLog log = log("a.log");
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 10, 0, 0);
        ChatEntry before = new ChatEntry(log, base, 0, "hello");
        ChatEntry hit = new ChatEntry(log, base.plusMinutes(1), 1, "needle");
        ChatEntry after = new ChatEntry(log, base.plusMinutes(20), 2, "later");

        List<DisplayRow> rows = EntryClassifier.classify(List.of(before, hit, after),
            SearchFilter.defaults().withText("needle"));

        assertFalse(rows.get(0).match());
        assertTrue(rows.get(1).match());
        assertFalse(rows.get(2).match());
        assertEquals(Duration.ofMinutes(1), rows.get(0).distanceFromMatch());
        assertEquals(Duration.ZERO, rows.get(1).distanceFromMatch());
        assertEquals(Duration.ofMinutes(19), rows.get(2).distanceFromMatch());
        assertEquals(1, rows.get(1).highlights().size());
    }

    private static ChatLog log(String name) {
        LocalDateTime start = LocalDateTime.of(2026, 8, 26, 10, 0);
        return new ChatLog(new LogSource.File(Path.of(name)), LocalDate.of(2026, 8, 26), "26.2", start, start);
    }

    private static ChatEntry entry(ChatLog log, int line, String message) {
        return new ChatEntry(log, log.startTime().plusSeconds(line), line, message);
    }
}
