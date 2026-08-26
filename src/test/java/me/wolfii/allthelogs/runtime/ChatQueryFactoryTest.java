package me.wolfii.allthelogs.runtime;

import me.wolfii.allthelogs.data.ChatQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatQueryFactoryTest {
    @Test
    void emptyTextDoesNotAddAMessageFilter() {
        ChatQuery query = ChatQueryFactory.toQuery(SearchFilter.defaults());
        assertFalse(query.hasTextFilter());
        assertEquals(SearchFilter.DEFAULT_CONTEXT_LINES, query.contextLines());
        assertEquals(SearchFilter.DEFAULT_LIMIT, query.limit());
        assertEquals(ChatQuery.Sort.ASCENDING, query.sort());
    }

    @Test
    void substringIsCaseInsensitiveByDefault() {
        ChatQuery query = ChatQueryFactory.toQuery(SearchFilter.defaults().withText("Welcome"));
        assertEquals("Welcome", query.substring());
        assertFalse(query.caseSensitive());
        assertNull(query.regex());
    }

    @Test
    void caseSensitiveSubstringUsesTheDedicatedQueryMethod() {
        ChatQuery query = ChatQueryFactory.toQuery(
            SearchFilter.defaults().withText("Welcome").withCaseSensitive(true));
        assertEquals("Welcome", query.substring());
        assertTrue(query.caseSensitive());
    }

    @Test
    void regexAddsAnInlineCaseInsensitiveFlagUnlessAlreadyPresent() {
        assertEquals("(?i)foo.*bar", ChatQueryFactory.regexPattern("foo.*bar", false));
        assertEquals("foo.*bar", ChatQueryFactory.regexPattern("foo.*bar", true));
        assertEquals("(?i)already", ChatQueryFactory.regexPattern("(?i)already", false));
    }

    @Test
    void timelineQueryDropsContextLimitAndOffset() {
        LocalDateTime offset = LocalDateTime.of(2026, 1, 2, 3, 4);
        ChatQuery query = ChatQueryFactory.toTimelineQuery(
            SearchFilter.defaults().withText("hi").withContextLines(8).withLimit(50).withOffset(offset));
        assertEquals(0, query.contextLines());
        assertEquals(-1, query.limit());
        assertNull(query.offset());
        assertEquals("hi", query.substring());
    }
}
