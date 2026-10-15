package me.wolfii.allthelogs.data.playtime;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.FileSession;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.PlaceMark;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.VersionMark;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeSessionsTest {
    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2024, 3, 4, hour, minute);
    }

    @Test
    void wholeFileStaysOneSpanWhenNothingChanges() {
        List<PlaytimeSpan> spans = PlaytimeSessions.build(
            List.of(new FileSession(1, "1.20.4", at(10, 0), at(12, 0))),
            List.of(), List.of());

        assertEquals(1, spans.size());
        assertEquals(at(10, 0), spans.getFirst().start());
        assertEquals(at(12, 0), spans.getFirst().end());
        assertEquals("1.20.4", spans.getFirst().version());
        assertNull(spans.getFirst().server());
    }

    @Test
    void blankVersionBecomesUnknownAndZeroLengthFilesAreDropped() {
        List<PlaytimeSpan> spans = PlaytimeSessions.build(List.of(
            new FileSession(1, "  ", at(10, 0), at(11, 0)),
            new FileSession(2, "1.20", at(12, 0), at(12, 0))
        ), List.of(), List.of());

        assertEquals(1, spans.size());
        assertEquals(ChatLog.UNKNOWN_VERSION, spans.getFirst().version());
    }

    @Test
    void placeChangesSplitTheSessionAndTimeBeforeTheFirstPlaceStaysUnknown() {
        List<PlaytimeSpan> spans = PlaytimeSessions.build(
            List.of(new FileSession(7, "1.20.4", at(10, 0), at(12, 0))),
            List.of(
                new PlaceMark(7, at(10, 30), 1, "hypixel.net"),
                new PlaceMark(7, at(11, 0), 2, "localhost"),
                new PlaceMark(7, at(11, 40), 3, null)
            ),
            List.of());

        assertEquals(4, spans.size());
        assertNull(spans.get(0).server());
        assertEquals(at(10, 30), spans.get(0).end());
        assertEquals("hypixel.net", spans.get(1).server());
        assertEquals(at(11, 0), spans.get(1).end());
        assertEquals("localhost", spans.get(2).server());
        assertEquals(at(11, 40), spans.get(2).end());
        assertNull(spans.get(3).server());
        assertEquals(at(12, 0), spans.get(3).end());
        assertTrue(spans.stream().allMatch(span -> span.version().equals("1.20.4")));
    }

    @Test
    void versionMessageStartsANewVersionWithoutSplittingWhenItMatches() {
        List<PlaytimeSpan> spans = PlaytimeSessions.build(
            List.of(new FileSession(3, "1.21.1", at(9, 0), at(11, 0))),
            List.of(new PlaceMark(3, at(10, 0), 4, "hypixel.net")),
            List.of(
                new VersionMark(3, at(9, 30), 1, "1.21.1"),
                new VersionMark(3, at(10, 0), 2, "1.21.4")
            ));

        assertEquals(2, spans.size());
        assertEquals("1.21.1", spans.get(0).version());
        assertNull(spans.get(0).server());
        assertEquals(at(10, 0), spans.get(0).end());
        assertEquals("1.21.4", spans.get(1).version());
        assertEquals("hypixel.net", spans.get(1).server());
        assertEquals(at(11, 0), spans.get(1).end());
    }
}
