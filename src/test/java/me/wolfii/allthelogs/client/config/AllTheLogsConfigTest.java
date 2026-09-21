package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllTheLogsConfigTest {
    @TempDir
    Path temp;

    @Test
    void defaultsMatchCurrentBrowserBehavior() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("missing.json"));
        assertTrue(config.extraImportDirectories().isEmpty());
        assertFalse(config.hideImportButton());
    }

    @Test
    void saveDoesNotWriteTheInstanceDirectory() throws Exception {
        Path file = temp.resolve("allthelogs.json");
        Path instance = temp.resolve("instance");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setExtraImportDirectories(List.of(instance.toString(), temp.resolve("other").toString()), instance);
        config.setHideImportButton(true);
        config.setMessageFontSize(8);
        config.setDefaultContextLines(7);
        config.save();

        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        String otherLogs = temp.resolve("other").resolve("logs").toAbsolutePath().normalize().toString();
        assertEquals(List.of(otherLogs), loaded.extraImportDirectories());
        assertFalse(Files.readString(file).contains(instance.toAbsolutePath().normalize().toString()));
        assertTrue(loaded.hideImportButton());
        assertEquals(8, loaded.messageFontSize());
        assertEquals(7, loaded.defaultContextLines());
        assertFalse(Files.readString(file).contains("filterPersistence"));
        assertFalse(Files.readString(file).contains("\"filter\""));
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
    void roundTripsKnownJsonFieldsAndIgnoresRemovedPersistenceKeys() throws Exception {
        Path file = temp.resolve("allthelogs.json");
        Files.writeString(file, """
            {
              "extraImportDirectories": [
                "%s"
              ],
              "filterPersistence": "across-restarts",
              "hideImportButton": true,
              "messageFontSize": 9,
              "filter": {
                "text": "hello",
                "regex": true,
                "contextLines": 2
              }
            }
            """.formatted(temp.resolve("other").toAbsolutePath().normalize().toString().replace("\\", "\\\\")));

        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        String otherLogs = temp.resolve("other").resolve("logs").toAbsolutePath().normalize().toString();
        assertEquals(List.of(otherLogs), loaded.extraImportDirectories());
        assertTrue(loaded.hideImportButton());
        assertEquals(9, loaded.messageFontSize());
        assertEquals(SearchFilter.DEFAULT_CONTEXT_LINES, loaded.defaultContextLines());

        loaded.save();
        String json = Files.readString(file);
        assertTrue(json.contains("\"extraImportDirectories\""));
        assertTrue(json.contains("\"hideImportButton\": true"));
        assertTrue(json.contains("\"messageFontSize\": 9"));
        assertTrue(json.contains("\"defaultContextLines\""));
        assertFalse(json.contains("filterPersistence"));
        assertFalse(json.contains("regexFlags"));
    }
}
