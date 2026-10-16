package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.IngestFilter;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import me.wolfii.allthelogs.data.parse.TextRemoval;
import me.wolfii.allthelogs.data.query.CleanupPreview;
import me.wolfii.allthelogs.data.query.MessageCleanup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCleanupTest {
    @TempDir
    Path tempDir;

    private LogStore store;

    @AfterEach
    void tearDown() {
        if (store != null) store.close();
        IngestFilter.setIgnored(List.of());
    }

    private LogStore importedStore(LogStore target) throws IOException {
        LogFixtures.writePlain(tempDir.resolve("instance/logs"), "2026-08-24-1.log",
            LogFixtures.modernLog("26.2", "keep me", "spam [x3]", "spam [x12]", "[x2]", "hello"));
        target.importDirectory(tempDir.resolve("instance"));
        store = target;
        return target;
    }

    private List<String> messages() {
        return store.allEntries().stream().map(ChatEntry::message).toList();
    }

    @Test
    void previewCountsMatchesAndShowsOldestNewestAndSample() throws IOException {
        importedStore(LogStore.openInMemory());
        CleanupPreview preview = store.previewCleanup(ChatQuery.all(), 2, 5);
        assertEquals(5, preview.matches());
        assertEquals(List.of("keep me", "spam [x3]"), preview.oldest().stream().map(ChatEntry::message).toList());
        assertEquals(List.of("[x2]", "hello"), preview.newest().stream().map(ChatEntry::message).toList());
        assertEquals(List.of("spam [x12]"), preview.sample().stream().map(ChatEntry::message).toList());

        CleanupPreview few = store.previewCleanup(ChatQuery.all().withSubstring("spam"), 3, 5);
        assertEquals(List.of("spam [x3]", "spam [x12]"), few.oldest().stream().map(ChatEntry::message).toList());
        assertTrue(few.newest().isEmpty());
        assertTrue(few.sample().isEmpty());

        CleanupPreview none = store.previewCleanup(ChatQuery.all().withSubstring("absent"), 3, 5);
        assertEquals(0, none.matches());
        assertTrue(none.oldest().isEmpty());
    }

    @Test
    void deleteRemovesMatchesAndRefreshesTheStoredCount() throws IOException {
        importedStore(LogStore.openInMemory());
        MessageCleanup.Outcome outcome = store.deleteMatches(ChatQuery.all().withSubstring("spam"));
        assertEquals(2, outcome.deleted());
        assertEquals(List.of("keep me", "[x2]", "hello"), messages());
        assertEquals(3, store.metadata().chatEntryCount());
    }

    @Test
    void removingTextKeepsTheRestAndDeletesLinesLeftBlank() throws IOException {
        importedStore(LogStore.open(tempDir.resolve("store.duckdb")));
        ChatQuery query = ChatQuery.all().withRegex("\\s*\\[x\\d+\\]");
        MessageCleanup.Outcome outcome = store.removeMatchedText(query, Pattern.compile("\\s*\\[x\\d+\\]"));
        assertEquals(2, outcome.changed());
        assertEquals(1, outcome.deleted());
        assertEquals(List.of("keep me", "spam", "spam", "hello"), messages());
        assertEquals(4, store.metadata().chatEntryCount());
    }

    @Test
    void removalMovesFormattingWithTheRemainingCharacters() {
        int red = PackedFormatting.color(0xFF0000);
        int bold = PackedFormatting.BOLD;
        long[] packed = PackedFormatting.pack(new int[]{red, red, 0, 0, 0, bold, bold});
        FormattingCodes.Parsed kept = TextRemoval.remove(new FormattingCodes.Parsed("ab[x]cd", packed),
            Pattern.compile("\\[x\\]"));
        assertEquals("abcd", kept.text());
        assertArrayEquals(new int[]{red, red, bold, bold}, PackedFormatting.perChar(kept.formatting(), 4));

        FormattingCodes.Parsed untouched = new FormattingCodes.Parsed("abc", null);
        assertSame(untouched, TextRemoval.remove(untouched, Pattern.compile("x*")));
    }

    @Test
    void ignoredPatternsAreNotImportedOrCaptured() throws IOException {
        IngestFilter.setIgnored(List.of(Pattern.compile("^spam")));
        importedStore(LogStore.openInMemory());
        assertEquals(List.of("keep me", "[x2]", "hello"), messages());

        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 25, 12, 0);
        store.startSession("26.2", startedAt);
        assertFalse(store.importSessionMessage("spam live", null, startedAt.plusSeconds(1)));
        assertTrue(store.importSessionMessage("live", null, startedAt.plusSeconds(2)));
        assertEquals(List.of("keep me", "[x2]", "hello", "live"), messages());
    }
}
