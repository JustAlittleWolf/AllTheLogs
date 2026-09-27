package me.wolfii.allthelogs.client.config;

import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.parse.MessageCharacters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllTheLogsConfigTest {
    @TempDir
    Path temp;

    @Test
    void defaultsMatchCurrentBrowserBehavior() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("missing.json"));
        assertTrue(config.extraImportDirectories().isEmpty());
        assertEquals(AllTheLogsConfig.DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS, config.contextMessageBrightness());
        assertEquals(92, config.contextMessageBrightness());
        assertFalse(config.filterPrivateUseCharacters());
    }

    @Test
    void saveDoesNotWriteTheInstanceDirectory() throws Exception {
        Path file = temp.resolve("allthelogs.json");
        Path instance = temp.resolve("instance");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        config.setExtraImportDirectories(List.of(instance.toString(), temp.resolve("other").toString()), instance);
        config.setMessageFontSize(8);
        config.setDefaultContextLines(7);
        config.setContextMessageBrightness(55);
        config.save();

        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        String otherLogs = temp.resolve("other").resolve("logs").toAbsolutePath().normalize().toString();
        assertEquals(List.of(otherLogs), loaded.extraImportDirectories());
        assertFalse(Files.readString(file).contains(instance.toAbsolutePath().normalize().toString()));
        assertEquals(8, loaded.messageFontSize());
        assertEquals(7, loaded.defaultContextLines());
        assertEquals(55, loaded.contextMessageBrightness());
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
    void clampsContextMessageBrightnessToTenThroughOneHundred() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("brightness.json"));
        config.setContextMessageBrightness(1);
        assertEquals(AllTheLogsConfig.MIN_CONTEXT_MESSAGE_BRIGHTNESS, config.contextMessageBrightness());
        config.setContextMessageBrightness(150);
        assertEquals(AllTheLogsConfig.MAX_CONTEXT_MESSAGE_BRIGHTNESS, config.contextMessageBrightness());
        assertEquals(AllTheLogsConfig.DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS,
            AllTheLogsConfig.currentContextMessageBrightness());
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
        assertEquals(9, loaded.messageFontSize());
        assertEquals(SearchFilter.DEFAULT_CONTEXT_LINES, loaded.defaultContextLines());
        assertEquals(AllTheLogsConfig.DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS, loaded.contextMessageBrightness());

        loaded.save();
        String json = Files.readString(file);
        assertTrue(json.contains("\"extraImportDirectories\""));
        assertFalse(json.contains("hideImportButton"));
        assertTrue(json.contains("\"messageFontSize\": 9"));
        assertTrue(json.contains("\"defaultContextLines\""));
        assertTrue(json.contains("\"contextMessageBrightness\": 92"));
        assertFalse(json.contains("filterPersistence"));
        assertFalse(json.contains("regexFlags"));
    }

    @Test
    void loadsContextMessageBrightnessFromJson() throws Exception {
        Path file = temp.resolve("brightness.json");
        Files.writeString(file, """
            {
              "contextMessageBrightness": 75
            }
            """);
        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        assertEquals(75, loaded.contextMessageBrightness());

        Files.writeString(file, """
            {
              "contextMessageBrightness": 10
            }
            """);
        assertEquals(10, AllTheLogsConfig.load(file).contextMessageBrightness());

        Files.writeString(file, """
            {
              "contextMessageBrightness": 100
            }
            """);
        assertEquals(100, AllTheLogsConfig.load(file).contextMessageBrightness());
    }

    @Test
    void privateUseFilterDefaultsOffAndRoundTrips() throws Exception {
        Path file = temp.resolve("private-use.json");
        AllTheLogsConfig loaded = AllTheLogsConfig.load(file);
        assertFalse(loaded.filterPrivateUseCharacters());
        loaded.save();
        assertTrue(Files.readString(file).contains("\"filterPrivateUseCharacters\": false"));

        Files.writeString(file, """
            {
              "filterPrivateUseCharacters": true
            }
            """);
        AllTheLogsConfig enabled = AllTheLogsConfig.load(file);
        assertTrue(enabled.filterPrivateUseCharacters());
        assertFalse(MessageCharacters.dropPrivateUseCharacters());
        enabled.setFilterPrivateUseCharacters(true);
        assertFalse(MessageCharacters.dropPrivateUseCharacters());
        enabled.save();
        assertTrue(Files.readString(file).contains("\"filterPrivateUseCharacters\": true"));
    }

    @Test
    void messagesCategoryComesBeforeAutoImport() {
        List<String> categories = new ArrayList<>();
        for (Field field : AllTheLogsConfig.class.getDeclaredFields()) {
            AutoGen autoGen = field.getAnnotation(AutoGen.class);
            if (autoGen == null) continue;
            if (categories.isEmpty() || !categories.getLast().equals(autoGen.category())) {
                categories.add(autoGen.category());
            }
        }
        assertEquals(List.of("browser", "import"), categories);
    }
}
