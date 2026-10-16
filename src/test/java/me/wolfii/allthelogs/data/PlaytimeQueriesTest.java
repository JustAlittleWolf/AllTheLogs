package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.importer.ImportOptions;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeQueriesTest {
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

    @Test
    void playtimeFollowsLogBoundsAndPlaceChanges() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("2024-03-04-1.log"), """
            [10:00:00] [main/INFO]: Loading Minecraft 1.20.4 with Fabric Loader 0.19.3
            [10:00:01] [Render thread/INFO]: Connecting to hypixel.net, 25565
            [10:30:00] [Render thread/INFO]: [CHAT] hello
            [11:00:00] [Render thread/INFO]: Connecting to localhost, 25565
            [11:00:10] [Render thread/INFO]: [CHAT] world
            [12:00:00] [Render thread/INFO]: [CHAT] bye
            """);
        Files.writeString(logs.resolve("2024-03-05-1.log"), """
            [09:00:00] [main/INFO]: Loading Minecraft 1.21.1 with Fabric Loader 0.19.3
            [09:00:01] [Render thread/INFO]: Connecting to hypixel.net, 25565
            [10:00:00] [Render thread/INFO]: [CHAT] Loading Minecraft 1.21.4 with Fabric Loader 9
            [11:00:00] [Render thread/INFO]: [CHAT] still
            """);

        store.importDirectory(logs, ImportOptions.defaults().withOptimize(false));
        List<PlaytimeSpan> spans = store.playtime().stream()
            .sorted(java.util.Comparator.comparing(PlaytimeSpan::start))
            .toList();

        assertEquals(5, spans.size());
        assertSpan(spans.get(0), LocalDateTime.of(2024, 3, 4, 10, 0), LocalDateTime.of(2024, 3, 4, 10, 30),
            "1.20.4", null);
        assertSpan(spans.get(1), LocalDateTime.of(2024, 3, 4, 10, 30), LocalDateTime.of(2024, 3, 4, 11, 0, 10),
            "1.20.4", "hypixel.net");
        assertSpan(spans.get(2), LocalDateTime.of(2024, 3, 4, 11, 0, 10), LocalDateTime.of(2024, 3, 4, 12, 0),
            "1.20.4", "localhost");
        assertSpan(spans.get(3), LocalDateTime.of(2024, 3, 5, 9, 0), LocalDateTime.of(2024, 3, 5, 10, 0),
            "1.21.1", null);
        assertSpan(spans.get(4), LocalDateTime.of(2024, 3, 5, 10, 0), LocalDateTime.of(2024, 3, 5, 11, 0),
            "1.21.1", "hypixel.net");
        long total = spans.stream().mapToLong(PlaytimeSpan::millis).sum();
        assertTrue(total == 4 * 3_600_000L);
    }

    @Test
    void aDebugLogCoveringTheSameSessionIsNotCountedTwice() throws IOException {
        Path logs = tempDir.resolve("overlap");
        Files.createDirectories(logs);
        String dated = """
            [10:00:00] [main/INFO]: Loading Minecraft 1.20.4 with Fabric Loader 0.19.3
            [10:00:01] [Render thread/INFO]: Connecting to hypixel.net, 25565
            [12:00:00] [Render thread/INFO]: [CHAT] done
            """;
        String debugLog = """
            [10:00:00] [main/INFO]: Loading Minecraft 1.20.4 with Fabric Loader 0.19.3
            [10:00:01] [Render thread/INFO]: Connecting to hypixel.net, 25565
            [11:00:00] [Render thread/INFO]: [CHAT] from the debug log
            [12:00:00] [Render thread/INFO]: [CHAT] done
            """;
        Files.writeString(logs.resolve("2024-03-06-1.log"), dated);
        Path debug = logs.resolve("debug.log");
        Files.writeString(debug, debugLog);
        Files.setLastModifiedTime(debug, FileTime.from(
            LocalDateTime.of(2024, 3, 6, 12, 0).atZone(ZoneId.systemDefault()).toInstant()));

        store.importDirectory(logs, ImportOptions.defaults().withOptimize(false));
        List<PlaytimeSpan> spans = store.playtime();
        assertEquals(2 * 3_600_000L, spans.stream().mapToLong(PlaytimeSpan::millis).sum());
        assertTrue(spans.stream().allMatch(span -> span.version().equals("1.20.4")));
    }

    private static void assertSpan(PlaytimeSpan span, LocalDateTime start, LocalDateTime end, String version, String server) {
        assertEquals(start, span.start());
        assertEquals(end, span.end());
        assertEquals(version, span.version());
        assertEquals(server, span.server());
    }
}
