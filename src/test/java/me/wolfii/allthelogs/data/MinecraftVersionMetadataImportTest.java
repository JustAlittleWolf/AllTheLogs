package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.importer.ImportOptions;
import me.wolfii.allthelogs.data.session.SessionMarker;
import me.wolfii.allthelogs.data.store.StoreConnections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftVersionMetadataImportTest {
    @TempDir
    Path tempDir;

    private LogStore store;

    @BeforeEach
    void setUp() {
        store = LogStore.openInMemory();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static ImportOptions metadataOnly() {
        return ImportOptions.defaults()
            .withOptimize(false)
            .withSkipAlreadyImported(false)
            .withUpdateMetadataOnly(true);
    }

    private Path logDirectory() {
        return tempDir.resolve("instance/logs");
    }

    private String versionFor(String message) {
        return store.allEntries().stream()
            .filter(entry -> entry.message().equals(message))
            .findFirst().orElseThrow().chatLog().minecraftVersion();
    }

    @Test
    void fillsUnknownArchivedVersionWithoutAddingOrCountingMessagesTwice() throws IOException {
        Path archive = tempDir.resolve("logs.zip");
        String entry = "logs/2021-06-26-1.log.gz";
        LogFixtures.writeZip(archive, Map.of(entry, """
            [10:00:00] [main/INFO]: Starting Minecraft
            [10:00:10] [Render thread/INFO]: [CHAT] hello
            """));
        store.importArchive(archive, ImportOptions.defaults().withOptimize(false));
        assertEquals("unknown", versionFor("hello"));

        LogFixtures.writeZip(archive, Map.of(entry, """
            [10:00:00] [main/INFO]: Loading for game Minecraft 1.17
            [10:00:10] [Render thread/INFO]: [CHAT] hello
            """));
        var result = store.importArchive(archive, metadataOnly());

        assertEquals("1.17", versionFor("hello"));
        assertEquals(1, store.chatLogs().size());
        assertEquals(1, store.allEntries().size());
        assertEquals(1, result.importedFiles());
    }

    @Test
    void repairsStoredChatFreeLogEvenWhenReparsedFileHasNoTimestampsOrReload() throws IOException {
        Path logs = logDirectory();
        LogFixtures.writePlain(logs, "2026-08-26-1.log", """
            [10:00:00] [main/INFO]: Starting Minecraft
            [10:00:10] [Render thread/INFO]: Reloading ResourceManager: vanilla
            """);
        store.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false));
        LogFixtures.writePlain(logs, "2026-08-26-1.log", "Loading for game Minecraft 1.17\n");

        var result = store.importDirectory(tempDir, metadataOnly());

        assertEquals("1.17", store.chatLogs().getFirst().minecraftVersion());
        assertEquals(1, result.importedFiles());
        assertEquals(0, result.importedEntries());
    }

    @Test
    void fillsUnknownVersionOnStoredFileWithoutChatEntries() throws IOException {
        Path logs = logDirectory();
        LogFixtures.writePlain(logs, "2026-08-26-1.log", """
            [10:00:00] [main/INFO]: Starting Minecraft
            [10:00:10] [Worker-Main-1/INFO]: Reloading ResourceManager: Default
            """);
        store.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false));
        assertEquals("unknown", store.chatLogs().getFirst().minecraftVersion());

        LogFixtures.writePlain(logs, "2026-08-26-1.log", """
            [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
            [10:00:10] [Worker-Main-1/INFO]: Reloading ResourceManager: Default
            """);
        var result = store.importDirectory(tempDir, metadataOnly());

        assertEquals("26.2", store.chatLogs().getFirst().minecraftVersion());
        assertEquals(1, result.importedFiles(), "the version-only file repair is reported as imported work");
        assertEquals(0, result.importedEntries());
    }

    @Test
    void preservesKnownVersionUnlessVersionOverwriteIsChosen() throws IOException {
        Path logs = logDirectory();
        LogFixtures.writePlain(logs, "2026-08-26-1.log", LogFixtures.modernLog("1.20.1", "hello"));
        store.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false));

        LogFixtures.writePlain(logs, "2026-08-26-1.log", LogFixtures.modernLog("1.21.4", "hello"));
        store.importDirectory(tempDir, metadataOnly());
        assertEquals("1.20.1", versionFor("hello"));

        store.importDirectory(tempDir, metadataOnly().withOverwriteMinecraftVersion(true));
        assertEquals("1.21.4", versionFor("hello"));
    }

    @Test
    void unknownParsedVersionDoesNotClearStoredKnownVersion() throws IOException {
        Path logs = logDirectory();
        LogFixtures.writePlain(logs, "2026-08-26-1.log", LogFixtures.modernLog("1.20.1", "hello"));
        store.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false));

        LogFixtures.writePlain(logs, "2026-08-26-1.log", """
            [10:00:00] [main/INFO]: Starting Minecraft
            [10:00:10] [Render thread/INFO]: [CHAT] hello
            """);
        store.importDirectory(tempDir, metadataOnly().withOverwriteMinecraftVersion(true));

        assertEquals("1.20.1", versionFor("hello"));
    }

    @Test
    void fillsBlankStoredVersion() throws Exception {
        Path database = tempDir.resolve("store.duckdb");
        store.close();
        store = LogStore.open(database);
        LogFixtures.writePlain(logDirectory(), "2026-08-26-1.log", LogFixtures.modernLog("1.20.1", "hello"));
        store.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false));
        store.close();
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("UPDATE log_file SET minecraft_version = ' ' ");
        }
        store = LogStore.open(database);

        LogFixtures.writePlain(logDirectory(), "2026-08-26-1.log", LogFixtures.modernLog("1.21.4", "hello"));
        store.importDirectory(tempDir, metadataOnly());

        assertEquals("1.21.4", versionFor("hello"));
    }

    @Test
    void unmatchedKnownLogDoesNotCreateAStoredLogInMetadataOnlyMode() throws IOException {
        Path logs = logDirectory();
        LogFixtures.writePlain(logs, "2026-08-26-1.log", LogFixtures.modernLog("1.20.1", "stored"));
        store.importDirectory(tempDir, ImportOptions.defaults().withOptimize(false));
        LogFixtures.writePlain(logs, "2026-08-27-1.log", LogFixtures.modernLog("1.21.4", "unmatched"));

        store.importDirectory(tempDir, metadataOnly());

        assertEquals(1, store.chatLogs().size());
        assertEquals(1, store.allEntries().size());
    }

    @Test
    void liveSessionVersionNeedsOverwriteLiveWhenReplacingKnownValue() throws IOException {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 12, 0);
        var session = store.startSession("1.20.1", startedAt);
        String id = ((LogSource.Session) session.source()).id();
        store.importSessionMessage("live", startedAt);
        LogFixtures.writePlain(logDirectory(), "2026-08-26-1.log", """
            [10:00:00] [main/INFO]: Loading Minecraft 1.21.4 with Fabric Loader 0.19.3
            [10:00:02] [allthelogs-store/INFO]: %s
            [10:00:10] [Render thread/INFO]: [CHAT] live
            """.formatted(SessionMarker.message(id)));

        store.importDirectory(tempDir, metadataOnly().withOverwriteMinecraftVersion(true));
        assertEquals("1.20.1", versionFor("live"));

        store.importDirectory(tempDir, metadataOnly().withOverwriteMinecraftVersion(true).withOverwriteLive(true));
        assertEquals("1.21.4", versionFor("live"));
    }

    @Test
    void fillsUnknownLiveVersionFromItsSessionMarkerWithoutAnOverwrite() throws IOException {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 10, 0);
        var session = store.startSession("unknown", startedAt);
        String id = ((LogSource.Session) session.source()).id();
        store.importSessionMessage("live", startedAt.plusSeconds(10));
        writeMarkedLog("2026-08-26-1.log", id, "1.21.4");

        store.importDirectory(tempDir, metadataOnly());

        assertEquals("1.21.4", versionFor("live"));
        assertEquals(1, store.chatLogs().size());
    }

    @Test
    void conflictingVersionsForTheSameLiveSessionAreNotChosenByImportOrder() throws IOException {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 26, 10, 0);
        var session = store.startSession("unknown", startedAt);
        String id = ((LogSource.Session) session.source()).id();
        store.importSessionMessage("live", startedAt.plusSeconds(10));
        writeMarkedLog("2026-08-26-1.log", id, "1.20.1");
        writeMarkedLog("2026-08-26-2.log", id, "1.21.4");

        store.importDirectory(tempDir, metadataOnly().withOverwriteMinecraftVersion(true).withOverwriteLive(true));

        assertEquals("unknown", versionFor("live"));
        assertEquals(1, store.chatLogs().size());
    }

    private void writeMarkedLog(String name, String id, String version) throws IOException {
        LogFixtures.writePlain(logDirectory(), name, """
            [10:00:00] [main/INFO]: Loading Minecraft %s with Fabric Loader 0.19.3
            [10:00:02] [allthelogs-store/INFO]: %s
            [10:00:10] [Render thread/INFO]: [CHAT] live
            """.formatted(version, SessionMarker.message(id)));
    }
}
