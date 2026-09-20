package me.wolfii.allthelogs.client.config;

import com.google.gson.JsonObject;
import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterPersistenceTest {
    @TempDir
    Path temp;

    @AfterEach
    void resetSession() {
        FilterPersistence.clearSession();
    }

    @Test
    void notPersistedAlwaysOpensAtDefaultsEvenAfterARememberedSearch() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("cfg.json"));
        config.setFilterPersistence(FilterPersistence.NOT_PERSISTED);
        FilterPersistence.remember(SearchFilter.defaults().withText("kept"), config, true);
        assertEquals("", FilterPersistence.openingFilter(config).text());
        assertTrue(FilterPersistence.openingFilter(config).equals(SearchFilter.defaults()));
    }

    @Test
    void sessionModeRemembersWithoutWritingTheFile() {
        Path file = temp.resolve("cfg.json");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setFilterPersistence(FilterPersistence.SESSION);
        FilterPersistence.remember(SearchFilter.defaults().withText("session-query"), config, true);
        assertEquals("session-query", FilterPersistence.openingFilter(config).text());
        AllTheLogsConfig reloaded = AllTheLogsConfig.load(file);
        assertEquals("", reloaded.persistedFilter().text());
    }

    @Test
    void acrossRestartsWritesTheFilterToDisk() {
        Path file = temp.resolve("cfg.json");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setFilterPersistence(FilterPersistence.ACROSS_RESTARTS);
        FilterPersistence.remember(SearchFilter.defaults().withText("disk-query"), config, true);
        FilterPersistence.clearSession();
        AllTheLogsConfig reloaded = AllTheLogsConfig.load(file);
        assertEquals("disk-query", reloaded.persistedFilter().text());
        assertEquals("disk-query", FilterPersistence.openingFilter(reloaded).text());
    }

    @Test
    void jsonRoundTripKeepsSearchFieldsAndDropsPaging() {
        SearchFilter filter = SearchFilter.defaults()
            .withText("hello")
            .withRegex(true)
            .withCaseSensitive(true)
            .withContextLines(6)
            .withStartingAt(LocalDateTime.of(2026, 1, 2, 3, 4))
            .withUpUntil(LocalDateTime.of(2026, 2, 3, 4, 5))
            .withVersion("26.2")
            .withLimit(25)
            .withOffset(LocalDateTime.of(2026, 1, 2, 3, 5));
        SearchFilter restored = FilterPersistence.fromJson(FilterPersistence.toJson(filter));
        assertEquals("hello", restored.text());
        assertTrue(restored.regex());
        assertTrue(restored.caseSensitive());
        assertEquals(6, restored.contextLines());
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), restored.startingAt());
        assertEquals(LocalDateTime.of(2026, 2, 3, 4, 5), restored.upUntil());
        assertEquals("26.2", restored.version());
        assertEquals(SearchFilter.DEFAULT_LIMIT, restored.limit());
        assertNull(restored.offset());
    }

    @Test
    void fromJsonIgnoresBrokenValues() {
        JsonObject json = new JsonObject();
        json.add("text", new JsonObject());
        json.addProperty("contextLines", "nope");
        json.addProperty("startingAt", "not-a-date");
        SearchFilter restored = FilterPersistence.fromJson(json);
        assertEquals("", restored.text());
        assertEquals(SearchFilter.DEFAULT_CONTEXT_LINES, restored.contextLines());
        assertNull(restored.startingAt());
    }
}
