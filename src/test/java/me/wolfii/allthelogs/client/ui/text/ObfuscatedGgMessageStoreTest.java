package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.LogStore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opens the reporter's portable database when {@code allthelogs.database} (or {@code /tmp/logs.duckdb})
 * is present. The newest-page query is what the browser runs on an unfiltered chronological list.
 */
class ObfuscatedGgMessageStoreTest {
    private static final String GG = "[-] schnellemitte: -- gg --";

    @TempDir
    Path tempDir;

    private static Path sourceDatabase() {
        String configured = System.getProperty("allthelogs.database");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path fallback = Path.of("/tmp/logs.duckdb");
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    @Test
    void newestBrowserPageContainsTheObfuscatedGgLine() throws IOException {
        Path source = sourceDatabase();
        assumeTrue(source != null && Files.isRegularFile(source), "reporter database not available");
        Path copy = tempDir.resolve("logs.duckdb");
        Files.copy(source, copy);

        try (LogStore store = LogStore.open(copy)) {
            List<ChatEntry> newest = store.findEntries(SearchFilter.defaults()
                .withSort(ChatQuery.Sort.DESCENDING)
                .toQuery());
            assertEquals(SearchFilter.DEFAULT_LIMIT, newest.size());
            ChatEntry gg = newest.stream()
                .filter(entry -> GG.equals(entry.message()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected " + GG + " among the newest "
                    + SearchFilter.DEFAULT_LIMIT + " rows"));
            DisplayRow row = new DisplayRow(gg, true, List.of());
            Component drawn = MessageText.messageRange(row, 0, row.message().length());
            assertEquals(GG, drawn.getString());
            drawn.visit((style, text) -> {
                assertFalse(style.isObfuscated(), text);
                return Optional.empty();
            }, Style.EMPTY);
        }
    }
}
