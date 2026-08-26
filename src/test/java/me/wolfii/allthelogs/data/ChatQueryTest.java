package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatQueryTest {
    @Test
    void defaultsToAscendingWithNoOffsetOrLimit() {
        ChatQuery query = ChatQuery.all();

        assertEquals(ChatQuery.Sort.ASCENDING, query.sort());
        assertFalse(query.descending());
        assertNull(query.offset());
        assertEquals(-1, query.limit());
    }

    @Test
    void withSortReplacesDescending() {
        ChatQuery descending = ChatQuery.all().withDescending(true);
        assertEquals(ChatQuery.Sort.DESCENDING, descending.sort());
        assertTrue(descending.descending());

        ChatQuery ascending = descending.withSort(ChatQuery.Sort.ASCENDING);
        assertEquals(ChatQuery.Sort.ASCENDING, ascending.sort());
        assertFalse(ascending.descending());
    }

    @Test
    void withDescendingFalseRestoresAscending() {
        ChatQuery query = ChatQuery.all().withSort(ChatQuery.Sort.DESCENDING).withDescending(false);
        assertSame(ChatQuery.Sort.ASCENDING, query.sort());
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
}
