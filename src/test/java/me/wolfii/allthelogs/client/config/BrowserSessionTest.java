package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BrowserSessionTest {
    @TempDir
    Path temp;

    @AfterEach
    void resetSession() {
        BrowserSession.clear();
    }

    @Test
    void remembersTheFilterForThisRunWithoutWritingTheFile() throws Exception {
        Path file = temp.resolve("cfg.json");
        AllTheLogsConfig config = AllTheLogsConfig.load(file);
        BrowserSession.remember(SearchFilter.defaults().withText("session-query"));
        config.save();
        assertEquals("session-query", BrowserSession.openingFilter(config).text());
        assertFalse(Files.readString(file).contains("session-query"));
        BrowserSession.clear();
        assertEquals("", BrowserSession.openingFilter(AllTheLogsConfig.load(file)).text());
    }

    @Test
    void emptySessionUsesTheConfiguredDefaultContextLines() {
        AllTheLogsConfig config = AllTheLogsConfig.load(temp.resolve("cfg.json"));
        config.setDefaultContextLines(9);
        assertEquals(9, BrowserSession.openingFilter(config).contextLines());
        BrowserSession.remember(SearchFilter.defaults().withContextLines(2).withText("kept"));
        assertEquals(2, BrowserSession.openingFilter(config).contextLines());
        assertEquals("kept", BrowserSession.openingFilter(config).text());
    }
}
