package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllTheLogsConfigTest {
    @TempDir
    Path temp;

    @Test
    void defaultsMatchCurrentBrowserBehavior() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("missing.json"));
        assertTrue(config.extraImportDirectories().isEmpty());
        assertEquals(FilterPersistence.NOT_PERSISTED, config.filterPersistence());
        assertFalse(config.hideImportButton());
        assertEquals(AllTheLogsConfig.DEFAULT_MESSAGE_FONT_SIZE, config.messageFontSize());
        assertEquals(12, config.messageFontSize());
        assertEquals(SearchFilter.defaults(), config.persistedFilter());
    }

    @Test
    void saveDoesNotWriteTheInstanceDirectory() throws Exception {
        Path file = temp.resolve("allthelogs.json");
        Path instance = temp.resolve("instance");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setExtraImportDirectories(List.of(instance.toString(), temp.resolve("other").toString()), instance);
        config.setFilterPersistence(FilterPersistence.SESSION);
        config.setHideImportButton(true);
        config.setMessageFontSize(8);
        config.save();

        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        String other = temp.resolve("other").toAbsolutePath().normalize().toString();
        assertEquals(List.of(other), loaded.extraImportDirectories());
        assertFalse(Files.readString(file).contains(instance.toAbsolutePath().normalize().toString()));
        assertEquals(FilterPersistence.SESSION, loaded.filterPersistence());
        assertTrue(loaded.hideImportButton());
        assertEquals(8, loaded.messageFontSize());
    }

    @Test
    void persistsTheSearchFilterAcrossRestarts() {
        Path file = temp.resolve("allthelogs.json");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        SearchFilter filter = SearchFilter.defaults()
            .withText("hello")
            .withRegex(true)
            .withCaseSensitive(true)
            .withContextLines(6)
            .withStartingAt(LocalDateTime.of(2026, 1, 2, 3, 4))
            .withVersion("26.2")
            .withServerOrWorld("hypixel.net");
        config.setPersistedFilter(filter);
        config.setFilterPersistence(FilterPersistence.ACROSS_RESTARTS);
        config.save();

        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        assertEquals("hello", loaded.persistedFilter().text());
        assertTrue(loaded.persistedFilter().regex());
        assertTrue(loaded.persistedFilter().caseSensitive());
        assertEquals(6, loaded.persistedFilter().contextLines());
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4), loaded.persistedFilter().startingAt());
        assertEquals("26.2", loaded.persistedFilter().version());
        assertEquals("hypixel.net", loaded.persistedFilter().serverOrWorld());
        assertEquals(FilterPersistence.ACROSS_RESTARTS, loaded.filterPersistence());
    }

    @Test
    void clampsFontSizeToTheSupportedRange() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("font.json"));
        config.setMessageFontSize(1);
        assertEquals(AllTheLogsConfig.MIN_MESSAGE_FONT_SIZE, config.messageFontSize());
        config.setMessageFontSize(99);
        assertEquals(AllTheLogsConfig.MAX_MESSAGE_FONT_SIZE, config.messageFontSize());
    }

    @Test
    void unknownPersistenceFallsBackToNotPersisted() {
        assertEquals(FilterPersistence.NOT_PERSISTED, FilterPersistence.fromConfig(null));
        assertEquals(FilterPersistence.NOT_PERSISTED, FilterPersistence.fromConfig("nope"));
        assertEquals(FilterPersistence.SESSION, FilterPersistence.fromConfig("session"));
        assertEquals(FilterPersistence.ACROSS_RESTARTS, FilterPersistence.fromConfig("across-restarts"));
        assertEquals(FilterPersistence.SESSION, FilterPersistence.NOT_PERSISTED.next());
        assertEquals(FilterPersistence.ACROSS_RESTARTS, FilterPersistence.SESSION.next());
        assertEquals(FilterPersistence.NOT_PERSISTED, FilterPersistence.ACROSS_RESTARTS.next());
    }
}
