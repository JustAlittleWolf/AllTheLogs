package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SearchFilterTest {
    @Test
    void emptyTextDoesNotAddAMessageFilter() {
        ChatQuery query = SearchFilter.defaults().toQuery();
        assertFalse(query.hasTextFilter());
        assertEquals(0, query.contextLines());
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
    void textSearchFetchesOneExtraContextLine() {
        assertEquals(1, SearchFilter.defaults().withText("hi").withContextLines(0).toQuery().contextLines());
        assertEquals(4, SearchFilter.defaults().withText("hi").toQuery().contextLines());
        assertEquals(0, SearchFilter.defaults().toQuery().contextLines());
        assertEquals(0, SearchFilter.defaults().withVersion("26.2").toQuery().contextLines());
        assertEquals(0, SearchFilter.defaults().withServerOrWorld("hypixel.net").toQuery().contextLines());
        assertEquals(0, SearchFilter.defaults()
            .withStartingAt(LocalDateTime.of(2026, 1, 1, 0, 0)).toQuery().contextLines());
    }

    @Test
    void versionFilterIsOmittedByDefaultAndAppliedWhenSet() {
        assertNull(SearchFilter.defaults().toQuery().version());
        assertEquals("26.2", SearchFilter.defaults().withVersion("26.2").toQuery().version());
        assertNull(SearchFilter.defaults().withVersion("ALL").toQuery().version());
        assertNull(SearchFilter.defaults().withVersion("  ").version());
    }

    @Test
    void serverFilterIsOmittedByDefaultAndAppliedWhenSet() {
        assertNull(SearchFilter.defaults().toQuery().serverOrWorld());
        assertEquals("hypixel.net", SearchFilter.defaults().withServerOrWorld("hypixel.net").toQuery().serverOrWorld());
        assertEquals("ALL", SearchFilter.defaults().withServerOrWorld("ALL").toQuery().serverOrWorld());
        assertNull(SearchFilter.defaults().withServerOrWorld("  ").serverOrWorld());
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
        String chatName = "(?:^|[\\]\\)\\>»\\s])([a-zA-Z0-9_]{3,16})(?:\\s*»|:)\\s+(?!(?:Offline|Online)\\b)\\S+.*";
        SearchFilter lookaround = SearchFilter.defaults().withText(chatName).withRegex(true);
        assertTrue(lookaround.invalidRegex());
        assertFalse(lookaround.canQuery());
    }

    @Test
    void emptyTextMatchesEverything() {
        assertTrue(SearchFilter.defaults().messagePredicate().test("anything"));
    }

    @Test
    void allowsContextKeepsTheDateWindowAndMatchingServers() {
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")),
            LocalDateTime.of(2026, 8, 26, 10, 0).toLocalDate(), "26.2",
            LocalDateTime.of(2026, 8, 26, 10, 0), LocalDateTime.of(2026, 8, 26, 10, 0));
        ChatEntry onHypixel = new ChatEntry(log, LocalDateTime.of(2026, 8, 26, 10, 5), 1, "hi",
            null, "player", "Hypixel.net");
        ChatEntry elsewhere = new ChatEntry(log, LocalDateTime.of(2026, 8, 26, 10, 6), 2, "hi",
            null, "player", "gommehd.net");
        ChatEntry tooEarly = new ChatEntry(log, LocalDateTime.of(2026, 8, 26, 9, 0), 0, "hi",
            null, "player", "Hypixel.net");
        SearchFilter server = SearchFilter.defaults().withServerOrWorld("hypixel");
        assertTrue(server.allowsContext(onHypixel));
        assertFalse(server.allowsContext(elsewhere));
        assertFalse(server.allowsContext(new ChatEntry(log, LocalDateTime.of(2026, 8, 26, 10, 7), 3, "hi")));
        SearchFilter window = SearchFilter.defaults()
            .withStartingAt(LocalDateTime.of(2026, 8, 26, 10, 0))
            .withUpUntil(LocalDateTime.of(2026, 8, 26, 11, 0));
        assertTrue(window.allowsContext(onHypixel));
        assertFalse(window.allowsContext(tooEarly));
        assertTrue(SearchFilter.defaults().allowsContext(elsewhere));
    }

    @Test
    void filterBarQueryOmitsTheSearchTerm() {
        SearchFilter filter = SearchFilter.defaults()
            .withText("needle")
            .withServerOrWorld("hypixel.net")
            .withVersion("26.2");
        ChatQuery bar = filter.toFilterBarQuery();
        assertNull(bar.substring());
        assertNull(bar.regex());
        assertEquals("hypixel.net", bar.serverOrWorld());
        assertEquals("26.2", bar.version());
        assertEquals("needle", filter.toQuery().substring());
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
        assertTrue(SearchFilter.defaults().withServerOrWorld("hypixel.net").isNarrowed());
        assertTrue(SearchFilter.defaults().withServerOrWorld("ALL").isNarrowed());
    }

    @Test
    void withDayFormatsUntilAsNextDayMidnight() {
        SearchFilter day = SearchFilter.defaults().withDay(java.time.LocalDate.of(2026, 8, 27));
        assertEquals(LocalDateTime.of(2026, 8, 27, 0, 0), day.startingAt());
        assertEquals(LocalDateTime.of(2026, 8, 28, 0, 0), day.upUntil());
        assertEquals("2026-08-27 00:00", DateParser.format(day.startingAt()));
        assertEquals("2026-08-28 00:00", DateParser.formatUntil(day.upUntil()));
    }
}
