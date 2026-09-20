package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserFilterMemoryTest {
    @TempDir
    Path temp;

    @AfterEach
    void resetSession() {
        BrowserFilterMemory.clearSession();
    }

    @Test
    void notPersistedAlwaysOpensAtDefaultsEvenAfterARememberedSearch() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("cfg.json"));
        config.setFilterPersistence(FilterPersistence.NOT_PERSISTED);
        BrowserFilterMemory.remember(SearchFilter.defaults().withText("kept"), config, true);
        assertEquals("", BrowserFilterMemory.openingFilter(config).text());
        assertTrue(BrowserFilterMemory.openingFilter(config).equals(SearchFilter.defaults()));
    }

    @Test
    void sessionModeRemembersWithoutWritingTheFile() {
        Path file = temp.resolve("cfg.json");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setFilterPersistence(FilterPersistence.SESSION);
        BrowserFilterMemory.remember(SearchFilter.defaults().withText("session-query"), config, true);
        assertEquals("session-query", BrowserFilterMemory.openingFilter(config).text());
        AllTheLogsConfig reloaded = AllTheLogsConfig.load(file);
        assertEquals("", reloaded.persistedFilter().text());
    }

    @Test
    void acrossRestartsWritesTheFilterToDisk() {
        Path file = temp.resolve("cfg.json");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setFilterPersistence(FilterPersistence.ACROSS_RESTARTS);
        BrowserFilterMemory.remember(SearchFilter.defaults().withText("disk-query"), config, true);
        BrowserFilterMemory.clearSession();
        AllTheLogsConfig reloaded = AllTheLogsConfig.load(file);
        assertEquals("disk-query", reloaded.persistedFilter().text());
        assertEquals("disk-query", BrowserFilterMemory.openingFilter(reloaded).text());
    }
}
