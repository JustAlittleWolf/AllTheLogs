package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.parse.LogParser;
import me.wolfii.allthelogs.data.parse.ParsedLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuationTest {
    private static final String BEFORE_MIDNIGHT = """
        [23:50:00] [main/INFO]: Loading Minecraft 1.21 with Fabric Loader 0.19.3
        [23:50:01] [main/INFO]: Setting user: Steve
        [23:50:05] [Render thread/INFO]: Connecting to mc.example.com, 25565
        [23:58:00] [Render thread/INFO]: [CHAT] before midnight
        [23:59:59] [Render thread/INFO]: [CHAT] last line of the day
        """;
    private static final String AFTER_MIDNIGHT = """
        [00:00:01] [Render thread/INFO]: [CHAT] after midnight
        [00:10:00] [Render thread/INFO]: Connecting to other.example.com, 25565
        [00:11:00] [Render thread/INFO]: [CHAT] on the other server
        """;
    private static final String NEXT_LAUNCH = """
        [00:20:00] [main/INFO]: Loading Minecraft 1.20.4 with Fabric Loader 0.19.3
        [00:20:01] [main/INFO]: Setting user: Alex
        [00:21:00] [Render thread/INFO]: [CHAT] fresh start
        """;

    @TempDir
    Path tempDir;

    @Test
    void parserTellsWhetherAFileStartsTheGameAndHowItsPlaceEnds() throws IOException {
        ParsedLog start = LogParser.parse(new BufferedReader(new StringReader(BEFORE_MIDNIGHT)));
        assertTrue(start.startup());
        assertNotNull(start.endPlace());
        assertFalse(start.endPlace().isEmpty());
        ParsedLog rest = LogParser.parse(new BufferedReader(new StringReader(AFTER_MIDNIGHT)));
        assertFalse(rest.startup());
        assertEquals(java.time.LocalTime.of(0, 10), rest.firstPlaceTime());
        ParsedLog quiet = LogParser.parse(new BufferedReader(new StringReader("[00:00:01] [Render thread/INFO]: [CHAT] hi\n")));
        assertNull(quiet.endPlace());
        ParsedLog left = LogParser.parse(new BufferedReader(new StringReader(
            BEFORE_MIDNIGHT + "[23:59:59] [Render thread/INFO]: Stopping!\n")));
        assertEquals("", left.endPlace());
    }

    @Test
    void aLogThatContinuesPastMidnightTakesTheVersionPlayerAndPlace() throws IOException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writePlain(logs, "2024-05-01-1.log", BEFORE_MIDNIGHT);
        LogFixtures.writePlain(logs, "2024-05-02-1.log", AFTER_MIDNIGHT);
        LogFixtures.writePlain(logs, "2024-05-02-2.log", NEXT_LAUNCH);
        try (LogStore store = LogStore.openInMemory()) {
            store.importDirectory(tempDir.resolve("instance"));
            assertContinued(store);
        }
    }

    @Test
    void aSessionOverTwoMidnightsPassesItsMetadataAlong() throws IOException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writePlain(logs, "2024-05-01-1.log", BEFORE_MIDNIGHT);
        LogFixtures.writePlain(logs, "2024-05-02-1.log", "[00:00:01] [Render thread/INFO]: [CHAT] day two\n"
            + "[23:59:58] [Render thread/INFO]: [CHAT] still here\n");
        LogFixtures.writePlain(logs, "2024-05-03-1.log", "[00:00:02] [Render thread/INFO]: [CHAT] day three\n");
        try (LogStore store = LogStore.openInMemory()) {
            store.importDirectory(tempDir.resolve("instance"));
            ChatLog third = log(store, "2024-05-03-1.log");
            assertEquals("1.21", third.minecraftVersion());
            assertEquals("Steve", third.minecraftUser());
            ChatEntry dayThree = entry(store, "day three");
            assertEquals(entry(store, "before midnight").serverOrWorld(), dayThree.serverOrWorld());
        }
    }

    @Test
    void aMetadataUpdateImportLinksLogsStoredBeforeContinuationsWereTracked() throws IOException, SQLException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writePlain(logs, "2024-05-01-1.log", BEFORE_MIDNIGHT);
        LogFixtures.writePlain(logs, "2024-05-02-1.log", AFTER_MIDNIGHT);
        LogFixtures.writePlain(logs, "2024-05-02-2.log", NEXT_LAUNCH);
        Path database = tempDir.resolve("store.duckdb");
        try (LogStore store = LogStore.open(database)) {
            store.importDirectory(tempDir.resolve("instance"), ImportOptions.defaults().withOptimize(false));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE log_file SET has_startup = NULL, end_place = NULL, first_place_time = NULL");
            statement.execute("UPDATE log_file SET minecraft_version = 'unknown', minecraft_user = NULL "
                + "WHERE file_name = '2024-05-02-1.log'");
            statement.execute("UPDATE chat_entry SET minecraft_user = NULL, server_or_world = NULL "
                + "WHERE file_id = (SELECT id FROM log_file WHERE file_name = '2024-05-02-1.log')");
        }
        try (LogStore store = LogStore.open(database)) {
            assertEquals("unknown", log(store, "2024-05-02-1.log").minecraftVersion());
            store.importDirectory(tempDir.resolve("instance"), ImportOptions.defaults()
                .withOptimize(false)
                .withSkipAlreadyImported(false)
                .withUpdateMetadataOnly(true)
                .withUpdateMinecraftUser(true)
                .withUpdateMinecraftServer(true));
            assertContinued(store);
        }
    }

    private static void assertContinued(LogStore store) {
        ChatLog continued = log(store, "2024-05-02-1.log");
        assertEquals("1.21", continued.minecraftVersion());
        assertEquals("Steve", continued.minecraftUser());
        ChatEntry before = entry(store, "before midnight");
        ChatEntry after = entry(store, "after midnight");
        assertEquals(before.serverOrWorld(), after.serverOrWorld());
        assertEquals("Steve", after.minecraftUser());
        assertTrue(entry(store, "on the other server").serverOrWorld().contains("other.example.com"));

        ChatLog fresh = log(store, "2024-05-02-2.log");
        assertEquals("1.20.4", fresh.minecraftVersion());
        assertEquals("Alex", fresh.minecraftUser());
    }

    private static ChatLog log(LogStore store, String fileName) {
        return store.chatLogs().stream()
            .filter(log -> log.source() instanceof LogSource.File file && file.path().getFileName().toString().equals(fileName))
            .findFirst().orElseThrow();
    }

    private static ChatEntry entry(LogStore store, String message) {
        List<ChatEntry> entries = store.allEntries();
        return entries.stream().filter(entry -> entry.message().equals(message))
            .min(Comparator.comparing(ChatEntry::timestamp)).orElseThrow();
    }
}
