package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.client.playtime.PlaytimeFilter;
import me.wolfii.allthelogs.client.search.ActiveFilters;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatLog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnknownTextTest {
    @Test
    void missingVersionAndPlaceShareOneLabel() {
        String unknown = UnknownText.get();
        assertEquals(unknown, UnknownText.version(null));
        assertEquals(unknown, UnknownText.version("  "));
        assertEquals(unknown, UnknownText.version(ChatLog.UNKNOWN_VERSION));
        assertEquals(unknown, UnknownText.version("UNKNOWN"));
        assertEquals(unknown, UnknownText.place(null));
        assertEquals(unknown, UnknownText.place(""));
        assertEquals(unknown, UnknownText.place(PlaytimeFilter.UNKNOWN_SERVER));
    }

    @Test
    void knownNamesStayAsStored() {
        assertEquals("1.21.1", UnknownText.version("1.21.1"));
        assertEquals("hypixel.net", UnknownText.place("hypixel.net"));
        assertEquals("unknown", UnknownText.place("unknown"));
        assertEquals("Gold World", UnknownText.place("\u00a76Gold \u00a7lWorld"));
    }

    @Test
    void activeFiltersUseThatSameLabel() {
        SearchFilter filter = SearchFilter.defaults()
            .withVersions(Set.of(ChatLog.UNKNOWN_VERSION))
            .withServersOrWorlds(Set.of(""));
        List<ActiveFilters.Entry> active = ActiveFilters.of(filter);
        assertEquals(List.of(
            new ActiveFilters.Entry(ActiveFilters.Kind.VERSION, UnknownText.get()),
            new ActiveFilters.Entry(ActiveFilters.Kind.SERVER, UnknownText.get())
        ), active);
    }
}
