package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllTheLogsSettingsTest {
    @Test
    void settingsRoundTripThroughJson(@TempDir Path dir) throws IOException {
        AllTheLogsSettings settings = new AllTheLogsSettings();
        settings.setContextLines(12);
        settings.setCaseSensitive(true);
        settings.setRegex(true);
        settings.setSort(ChatQuery.Sort.ASCENDING);

        Path file = dir.resolve("allthelogs.json");
        settings.save(file);
        String json = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(json.contains("\"limit\""));

        AllTheLogsSettings loaded = AllTheLogsSettings.load(file);

        assertEquals(12, loaded.contextLines());
        assertTrue(loaded.caseSensitive());
        assertTrue(loaded.regex());
        assertEquals(ChatQuery.Sort.ASCENDING, loaded.sort());
    }

    @Test
    void defaultsToNewestFirstAndIgnoresLegacyPageSize(@TempDir Path dir) throws IOException {
        AllTheLogsSettings defaults = new AllTheLogsSettings();
        assertEquals(ChatQuery.Sort.DESCENDING, defaults.sort());
        assertEquals(ChatQuery.Sort.DESCENDING, defaults.toFilter().sort());

        Path file = dir.resolve("allthelogs.json");
        Files.writeString(file, """
            {
              "contextLines": 4,
              "limit": 250,
              "caseSensitive": false,
              "regex": false,
              "sort": "DESCENDING"
            }
            """, StandardCharsets.UTF_8);

        AllTheLogsSettings loaded = AllTheLogsSettings.load(file);
        assertEquals(4, loaded.contextLines());
        assertEquals(ChatQuery.Sort.DESCENDING, loaded.sort());
        assertEquals(SearchFilter.DEFAULT_LIMIT, loaded.toFilter().limit());
    }
}
