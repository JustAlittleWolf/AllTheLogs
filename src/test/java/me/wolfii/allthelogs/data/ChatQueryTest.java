package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.api.ChatQuery.Sort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ChatQueryTest {
    @Test
    void withOffsetRequiresATimestamp() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withOffset(null));
        LocalDateTime offset = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertEquals(offset, ChatQuery.all().withOffset(offset).offset());
        assertNull(ChatQuery.all().withOffset(offset).offsetSource());
        assertEquals(12, ChatQuery.all().withSkip(12).skip());
        LogSource source = new LogSource.Session("cursor");
        ChatQuery row = ChatQuery.all().withOffset(offset, source, 4);
        assertEquals(offset, row.offset());
        assertEquals(source, row.offsetSource());
        assertEquals(4, row.offsetLine());
        ChatQuery timestampOnly = row.withOffset(offset.plusSeconds(1));
        assertNull(timestampOnly.offsetSource());
        assertEquals(0, timestampOnly.offsetLine());
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withOffset(offset, null, 0));
        assertThrows(IllegalArgumentException.class, () -> ChatQuery.all().withOffset(offset, source, -1));
    }

    @Test
    void withSortRejectsNull() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withSort(null));
    }

    @Test
    void startingAtAfterUpUntilIsRejected() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 1, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> ChatQuery.all().startingAt(start).upUntil(end));
        assertThrows(IllegalArgumentException.class, () -> ChatQuery.all().upUntil(end).startingAt(start));
    }

    @Test
    void startingAtAndUpUntilRejectNull() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().startingAt(null));
        assertThrows(NullPointerException.class, () -> ChatQuery.all().upUntil(null));
    }

    @Test
    void withVersionRejectsNull() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withVersion(null));
    }

    @Test
    void withServerOrWorldRejectsNull() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withServerOrWorld(null));
    }

    @Test
    void sortOppositeSwapsAscendingAndDescending() {
        assertEquals(Sort.DESCENDING, Sort.ASCENDING.opposite());
        assertEquals(Sort.ASCENDING, Sort.DESCENDING.opposite());
    }
}
