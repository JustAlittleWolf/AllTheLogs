package me.wolfii.allthelogs.client.view;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageListLayoutTest {
    @Test
    void extraContextIsTwiceTheFilterCappedAt100() {
        assertEquals(10, MessageListLayout.extraContextLines(5));
        assertEquals(100, MessageListLayout.extraContextLines(80));
        assertEquals(0, MessageListLayout.extraContextLines(0));
    }

    @Test
    void insertsADateHeaderAndAClusterGapBetweenDistantMatches() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow first = new DisplayRow(new ChatEntry(log, time, 0, "a"), true, Duration.ZERO, List.of());
        DisplayRow later = new DisplayRow(new ChatEntry(log, time.plusHours(1), 20, "b"), true, Duration.ZERO, List.of());
        MessageListLayout layout = MessageListLayout.of(List.of(first, later), 5);

        assertEquals(1, layout.dates().size());
        assertEquals(0, layout.dates().getFirst().y());
        assertEquals(MessageListLayout.DATE_HEIGHT, layout.rowY(0));
        assertEquals(MessageListLayout.DATE_HEIGHT + MessageListLayout.ROW_HEIGHT + MessageListLayout.CLUSTER_GAP,
            layout.rowY(1));
    }

    @Test
    void doesNotGapAdjacentLinesFromTheSameLog() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 10, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), time.toLocalDate(), "26.2", time, time);
        DisplayRow a = new DisplayRow(new ChatEntry(log, time, 0, "a"), true, Duration.ZERO, List.of());
        DisplayRow b = new DisplayRow(new ChatEntry(log, time.plusMinutes(1), 1, "b"), true, Duration.ZERO, List.of());
        MessageListLayout layout = MessageListLayout.of(List.of(a, b), 5);
        assertEquals(layout.rowY(0) + MessageListLayout.ROW_HEIGHT, layout.rowY(1));
        assertFalse(MessageListLayout.needsClusterGap(a, b, 5));
        assertTrue(MessageListLayout.needsClusterGap(a,
            new DisplayRow(new ChatEntry(log, time.plusHours(2), 12, "c"), true, Duration.ZERO, List.of()), 5));
    }

    @Test
    void stickyHeaderFollowsTheScrolledDate() {
        DisplayRow day1 = row("a.log", 0, LocalDateTime.of(2026, 8, 26, 10, 0));
        DisplayRow day2 = row("b.log", 0, LocalDateTime.of(2026, 8, 27, 10, 0));
        MessageListLayout layout = MessageListLayout.of(List.of(day1, day2), 5);
        assertEquals(LocalDate.of(2026, 8, 26), layout.stickyAt(0).date());
        assertEquals(LocalDate.of(2026, 8, 27), layout.stickyAt(layout.dates().get(1).y()).date());
    }

    private static DisplayRow row(String file, int line, LocalDateTime time) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of(file)), time.toLocalDate(), "26.2", time, time);
        ChatEntry entry = new ChatEntry(log, time, line, "msg-" + line);
        return new DisplayRow(entry, true, Duration.ZERO, List.of());
    }
}
