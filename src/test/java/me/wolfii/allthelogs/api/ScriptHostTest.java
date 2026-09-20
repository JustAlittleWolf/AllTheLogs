package me.wolfii.allthelogs.api;

import me.wolfii.allthelogs.client.script.ScriptFiles;
import me.wolfii.allthelogs.client.script.ScriptHost;
import me.wolfii.allthelogs.data.LogStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScriptHostTest {
    @TempDir
    Path tempDir;

    private LogStore store;
    private LogDatabase database;

    @BeforeEach
    void setUp() {
        store = LogStore.open(tempDir.resolve("logs.duckdb"));
        store.startSession("26.2", null);
        store.importSessionMessage("Welcome to the server", null);
        store.importSessionMessage("hello 26", null);
        store.importSessionMessage("other", null);
        store.startSession("1.21.1", null);
        store.importSessionMessage("legacy", null);
        database = new LogDatabase(store);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void exampleScriptQueriesWelcomeMatchesWithoutDumpingTheStore() throws Exception {
        String source = Files.readString(
            Path.of("src/main/resources/me/wolfii/allthelogs/client/script/example.ts"),
            StandardCharsets.UTF_8);
        Path output = tempDir.resolve("output").resolve("example.txt");
        ScriptHost.Result result = ScriptHost.execute(source, ScriptFiles.EXAMPLE, database, output);
        assertTrue(result.succeeded(), () -> result.console() + " / " + result.error());
        assertTrue(result.console().contains("4 entries"));
        assertTrue(result.console().contains("1 messages matching"));
        assertFalse(result.console().contains("Welcome to the server"));
        assertFalse(result.console().contains("hello 26"));
        assertFalse(result.console().contains("legacy"));
        assertEquals(output, result.outputFile());
        String written = Files.readString(output);
        assertTrue(written.contains("Welcome to the server"));
        assertFalse(written.contains("hello 26"));
        assertFalse(written.contains("legacy"));
    }

    @Test
    void chatQueryBindingsJoinThroughThePublicApi() throws Exception {
        Path output = tempDir.resolve("output").resolve("query.txt");
        ScriptHost.Result result = ScriptHost.execute("""
            const hits = database.findEntries(ChatQuery.all().withVersion("26.2"));
            writeToOutputFile("count=" + hits.length);
            hits.forEach((entry) => writeToOutputFile(entry.message));
            """, "query.js", database, output);
        assertTrue(result.succeeded(), () -> result.console() + " / " + result.error());
        String written = Files.readString(output);
        assertTrue(written.contains("count=3"));
        assertTrue(written.contains("Welcome to the server"));
        assertTrue(written.contains("hello 26"));
        assertTrue(written.contains("other"));
        assertFalse(written.contains("legacy"));
    }

    @Test
    void chatQueryStartingAtAcceptsIsoTimestampStrings() throws Exception {
        Path output = tempDir.resolve("output").resolve("range.txt");
        ScriptHost.Result result = ScriptHost.execute("""
            const first = allEntries()[0];
            const hits = database.findEntries(ChatQuery.all().startingAt(first.timestamp));
            writeToOutputFile("count=" + hits.length);
            """, "range.js", database, output);
        assertTrue(result.succeeded(), () -> result.console() + " / " + result.error());
        assertTrue(Files.readString(output).contains("count=4"));
    }
}
