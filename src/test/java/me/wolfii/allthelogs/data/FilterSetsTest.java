package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterSetsTest {
    @TempDir
    Path tempDir;

    private LogStore store;

    @BeforeEach
    void setUp() throws IOException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writePlain(logs, "2024-05-01-1.log", """
            [10:00:00] [main/INFO]: Loading Minecraft 1.21 with Fabric Loader 0.19.3
            [10:00:01] [Render thread/INFO]: [CHAT] before joining
            [10:00:02] [Render thread/INFO]: Connecting to a.net, 25565
            [10:00:03] [Render thread/INFO]: [CHAT] on a
            [10:00:04] [Render thread/INFO]: Connecting to b.net, 25565
            [10:00:05] [Render thread/INFO]: [CHAT] on b
            """);
        LogFixtures.writePlain(logs, "2024-05-02-1.log", """
            [10:00:00] [main/INFO]: Loading Minecraft 1.20.4 with Fabric Loader 0.19.3
            [10:00:02] [Render thread/INFO]: Connecting to a.net, 25565
            [10:00:03] [Render thread/INFO]: [CHAT] older version on a
            """);
        store = LogStore.openInMemory();
        store.importDirectory(tempDir.resolve("instance"));
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private List<String> messages(me.wolfii.allthelogs.api.ChatQuery query) {
        return store.findEntries(query).stream().map(ChatEntry::message).toList();
    }

    @Test
    void serverSetsMatchExactlyAndCanKeepMessagesWithoutAServer() {
        var all = me.wolfii.allthelogs.api.ChatQuery.all();
        assertEquals(List.of("on a", "older version on a"), messages(all.withServersOrWorlds(Set.of("a.net"))));
        assertEquals(List.of("before joining", "on b"), messages(all.withServersOrWorlds(
            Set.of("b.net", me.wolfii.allthelogs.api.ChatQuery.NO_SERVER_OR_WORLD))));
        assertEquals(List.of(), messages(all.withServersOrWorlds(Set.of())));
        assertEquals(List.of(), messages(all.withServersOrWorlds(Set.of("a"))));
    }

    @Test
    void versionSetsKeepEveryChosenVersion() {
        var all = me.wolfii.allthelogs.api.ChatQuery.all();
        assertEquals(List.of("older version on a"), messages(all.withVersions(Set.of("1.20.4"))));
        assertEquals(4, messages(all.withVersions(Set.of("1.20.4", "1.21"))).size());
        assertEquals(List.of(), messages(all.withVersions(Set.of())));
        assertEquals(List.of("on a"), messages(all.withVersions(Set.of("1.21")).withServersOrWorlds(Set.of("a.net"))));
    }
}
