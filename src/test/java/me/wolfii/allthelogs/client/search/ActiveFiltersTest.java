package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.api.ChatQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveFiltersTest {
    @Test
    void defaultsLeaveTheButtonUnmarked() {
        assertTrue(ActiveFilters.of(SearchFilter.defaults()).isEmpty());
    }

    @Test
    void searchTextDoesNotCount() {
        assertTrue(ActiveFilters.of(SearchFilter.defaults().withText("hello")).isEmpty());
    }

    @Test
    void regexAndTheOtherSidebarFieldsCount() {
        SearchFilter filter = SearchFilter.defaults()
            .withRegex(true)
            .withCaseSensitive(true)
            .withContextLines(12)
            .withStartingAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .withUpUntil(LocalDateTime.of(2026, 1, 2, 0, 0))
            .withVersion("26.2")
            .withServerOrWorld("hypixel.net")
            .withText("hello");
        List<ActiveFilters.Entry> active = ActiveFilters.of(filter);
        assertEquals(List.of(
            new ActiveFilters.Entry(ActiveFilters.Kind.REGEX, null),
            new ActiveFilters.Entry(ActiveFilters.Kind.CASE_SENSITIVE, null),
            new ActiveFilters.Entry(ActiveFilters.Kind.CONTEXT, "12"),
            new ActiveFilters.Entry(ActiveFilters.Kind.FROM, "2026-01-01 00:00"),
            new ActiveFilters.Entry(ActiveFilters.Kind.UNTIL, "2026-01-02 00:00"),
            new ActiveFilters.Entry(ActiveFilters.Kind.VERSION, "26.2"),
            new ActiveFilters.Entry(ActiveFilters.Kind.SERVER, "hypixel.net")
        ), active);
    }

    @Test
    void defaultContextAndAllVersionsStayInactive() {
        SearchFilter filter = SearchFilter.defaults().withVersions(null).withContextLines(SearchFilter.DEFAULT_CONTEXT_LINES);
        assertTrue(ActiveFilters.of(filter).isEmpty());
    }

    @Test
    void missingVersionAndPlaceShareOneLabel() {
        String unknown = ActiveFilters.name(null);
        assertEquals(unknown, ActiveFilters.name("  "));
        assertEquals(unknown, ActiveFilters.name(ChatQuery.NULL));
    }

    @Test
    void knownNamesStayAsStored() {
        assertEquals("1.21.1", ActiveFilters.name("1.21.1"));
        assertEquals("hypixel.net", ActiveFilters.name("hypixel.net"));
        assertEquals("unknown", ActiveFilters.name("unknown"));
        assertEquals("Gold World", ActiveFilters.name("\u00a76Gold \u00a7lWorld"));
    }

    @Test
    void nullPlaceholderShowsTheUnknownLabel() {
        SearchFilter filter = SearchFilter.defaults()
            .withVersions(Set.of(ChatQuery.NULL))
            .withServersOrWorlds(Set.of(ChatQuery.NULL));
        String unknown = ActiveFilters.name(null);
        assertEquals(List.of(
            new ActiveFilters.Entry(ActiveFilters.Kind.VERSION, unknown),
            new ActiveFilters.Entry(ActiveFilters.Kind.SERVER, unknown)
        ), ActiveFilters.of(filter));
    }
}
