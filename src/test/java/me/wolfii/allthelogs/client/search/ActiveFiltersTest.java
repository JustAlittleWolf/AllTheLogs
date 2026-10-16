package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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
}
