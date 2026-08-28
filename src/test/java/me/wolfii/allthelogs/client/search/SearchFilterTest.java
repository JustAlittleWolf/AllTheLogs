package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.data.ChatQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchFilterTest {
    @Test
    void emptyTextDoesNotAddAMessageFilter() {
        ChatQuery query = SearchFilter.defaults().toQuery();
        assertFalse(query.hasTextFilter());
        assertEquals(SearchFilter.DEFAULT_CONTEXT_LINES, query.contextLines());
        assertEquals(SearchFilter.DEFAULT_LIMIT, query.limit());
        assertEquals(ChatQuery.Sort.ASCENDING, query.sort());
        assertEquals(4, SearchFilter.DEFAULT_CONTEXT_LINES);
    }

    @Test
    void substringIsCaseInsensitiveByDefault() {
        ChatQuery query = SearchFilter.defaults().withText("Welcome").toQuery();
        assertEquals("Welcome", query.substring());
        assertFalse(query.caseSensitive());
        assertNull(query.regex());
    }

    @Test
    void caseSensitiveSubstringUsesTheDedicatedQueryMethod() {
        ChatQuery query = SearchFilter.defaults().withText("Welcome").withCaseSensitive(true).toQuery();
        assertEquals("Welcome", query.substring());
        assertTrue(query.caseSensitive());
    }

    @Test
    void regexAddsAnInlineCaseInsensitiveFlagUnlessAlreadyPresent() {
        assertEquals("(?i)foo.*bar", SearchFilter.regexPattern("foo.*bar", false));
        assertEquals("foo.*bar", SearchFilter.regexPattern("foo.*bar", true));
        assertEquals("(?i)already", SearchFilter.regexPattern("(?i)already", false));
    }

    @Test
    void summaryQueryDropsContextLimitAndOffset() {
        LocalDateTime offset = LocalDateTime.of(2026, 1, 2, 3, 4);
        ChatQuery query = SearchFilter.defaults()
            .withText("hi")
            .withContextLines(8)
            .withLimit(50)
            .withOffset(offset)
            .toSummaryQuery();
        assertEquals(0, query.contextLines());
        assertEquals(-1, query.limit());
        assertNull(query.offset());
        assertEquals("hi", query.substring());
    }

    @Test
    void versionFilterIsOmittedByDefaultAndAppliedWhenSet() {
        assertNull(SearchFilter.defaults().toQuery().version());
        assertEquals("26.2", SearchFilter.defaults().withVersion("26.2").toQuery().version());
        assertNull(SearchFilter.defaults().withVersion("ALL").toQuery().version());
        assertNull(SearchFilter.defaults().withVersion("  ").version());
    }

    @Test
    void substringIsCaseInsensitiveWhenMatchingInJava() {
        var matches = SearchFilter.defaults().withText("Needle").messagePredicate();
        assertTrue(matches.test("a needle here"));
        assertFalse(matches.test("haystack"));
    }

    @Test
    void invalidRegexNeverMatches() {
        var matches = SearchFilter.defaults().withText("(").withRegex(true).messagePredicate();
        assertFalse(matches.test("hello"));
        assertTrue(SearchFilter.compiledRegex("(", false).isEmpty());
        SearchFilter incomplete = SearchFilter.defaults().withText("(?i)(HoneY_D").withRegex(true);
        assertTrue(incomplete.invalidRegex());
        assertFalse(incomplete.canQuery());
        assertTrue(SearchFilter.defaults().withText("HoneY_D").withRegex(true).canQuery());
        assertTrue(SearchFilter.defaults().withText("(").canQuery());
    }

    @Test
    void emptyTextMatchesEverything() {
        assertTrue(SearchFilter.defaults().messagePredicate().test("anything"));
    }

    @Test
    void isNarrowedIgnoresSortPagingAndContext() {
        assertFalse(SearchFilter.defaults().isNarrowed());
        assertFalse(SearchFilter.defaults().withSort(ChatQuery.Sort.DESCENDING).isNarrowed());
        assertFalse(SearchFilter.defaults().withContextLines(12).isNarrowed());
        assertFalse(SearchFilter.defaults().withLimit(50).isNarrowed());
        assertFalse(SearchFilter.defaults().withRegex(true).withCaseSensitive(true).isNarrowed());
        assertTrue(SearchFilter.defaults().withText("hi").isNarrowed());
        assertTrue(SearchFilter.defaults().withVersion("26.2").isNarrowed());
        assertTrue(SearchFilter.defaults().withStartingAt(LocalDateTime.of(2026, 1, 1, 0, 0)).isNarrowed());
        assertTrue(SearchFilter.defaults().withUpUntil(LocalDateTime.of(2026, 1, 2, 0, 0)).isNarrowed());
        assertFalse(SearchFilter.defaults().withVersion("ALL").isNarrowed());
    }
}
