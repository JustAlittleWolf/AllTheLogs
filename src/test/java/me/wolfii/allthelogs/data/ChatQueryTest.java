package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatQueryTest {
    @Test
    void defaultsToAscendingWithNoOffsetLimitOrTimeBounds() {
        ChatQuery query = ChatQuery.all();

        assertEquals(ChatQuery.Sort.ASCENDING, query.sort());
        assertNull(query.offset());
        assertNull(query.startingAt());
        assertNull(query.upUntil());
        assertNull(query.version());
        assertEquals(-1, query.limit());
    }

    @Test
    void withSortReplacesThePreviousOrder() {
        ChatQuery descending = ChatQuery.all().withSort(ChatQuery.Sort.DESCENDING);
        assertEquals(ChatQuery.Sort.DESCENDING, descending.sort());

        ChatQuery ascending = descending.withSort(ChatQuery.Sort.ASCENDING);
        assertSame(ChatQuery.Sort.ASCENDING, ascending.sort());
    }

    @Test
    void withOffsetRequiresATimestamp() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withOffset(null));
        LocalDateTime offset = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertEquals(offset, ChatQuery.all().withOffset(offset).offset());
    }

    @Test
    void withSortRejectsNull() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withSort(null));
    }

    @Test
    void startingAtAndUpUntilAreIndependentBounds() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 2, 0, 0);

        ChatQuery fromStart = ChatQuery.all().startingAt(start);
        assertEquals(start, fromStart.startingAt());
        assertNull(fromStart.upUntil());

        ChatQuery untilEnd = ChatQuery.all().upUntil(end);
        assertNull(untilEnd.startingAt());
        assertEquals(end, untilEnd.upUntil());

        ChatQuery both = ChatQuery.all().startingAt(start).upUntil(end);
        assertEquals(start, both.startingAt());
        assertEquals(end, both.upUntil());
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
    void withVersionReplacesAndRejectsNull() {
        assertThrows(NullPointerException.class, () -> ChatQuery.all().withVersion(null));
        ChatQuery first = ChatQuery.all().withVersion("26.2");
        assertEquals("26.2", first.version());
        assertEquals("1.8.9", first.withVersion("1.8.9").version());
    }

    @Test
    void sortOppositeSwapsAscendingAndDescending() {
        assertEquals(ChatQuery.Sort.DESCENDING, ChatQuery.Sort.ASCENDING.opposite());
        assertEquals(ChatQuery.Sort.ASCENDING, ChatQuery.Sort.DESCENDING.opposite());
    }
}
