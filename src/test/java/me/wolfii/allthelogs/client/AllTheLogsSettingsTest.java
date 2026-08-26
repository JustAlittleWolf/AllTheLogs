package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.ChatQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllTheLogsSettingsTest {
    @Test
    void settingsRoundTripThroughJson(@TempDir Path dir) throws IOException {
        AllTheLogsSettings settings = new AllTheLogsSettings();
        settings.setContextLines(12);
        settings.setLimit(250);
        settings.setCaseSensitive(true);
        settings.setRegex(true);
        settings.setSort(ChatQuery.Sort.DESCENDING);

        Path file = dir.resolve("allthelogs.json");
        settings.save(file);
        AllTheLogsSettings loaded = AllTheLogsSettings.load(file);

        assertEquals(12, loaded.contextLines());
        assertEquals(250, loaded.limit());
        assertTrue(loaded.caseSensitive());
        assertTrue(loaded.regex());
        assertEquals(ChatQuery.Sort.DESCENDING, loaded.sort());
    }
}
