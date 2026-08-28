package me.wolfii.allthelogs.api;

import me.wolfii.allthelogs.client.LogStoreWorker;
import me.wolfii.allthelogs.data.LogStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogDatabaseTest {
    @TempDir
    Path tempDir;

    private LogStore store;
    private LogDatabase database;

    @BeforeEach
    void setUp() {
        store = LogStore.open(tempDir.resolve("logs.duckdb"));
        store.startSession("26.2", null);
        store.importSessionMessage("alpha", null);
        store.importSessionMessage("welcome beta", null);
        store.importSessionMessage("gamma", null);
        database = new LogDatabase(store);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void wrappingAnOpenStoreExposesQuerySurface() {
        assertTrue(database.isOpen());
        assertEquals(tempDir.resolve("logs.duckdb").toAbsolutePath().normalize(),
            database.databasePath().join().orElseThrow());
        assertEquals(3, database.allEntries().join().size());
        assertEquals(1, database.chatLogs().join().size());
        assertEquals(1, database.countMatches(ChatQuery.all().withSubstring("welcome")).join());
        assertEquals(1, database.summarizeMatches(ChatQuery.all().withSubstring("welcome")).join().matches());
        assertEquals(List.of("welcome beta"),
            database.findEntries(ChatQuery.all().withSubstring("welcome")).join()
                .stream().map(ChatEntry::message).toList());
        assertEquals(3, database.metadata().join().chatEntryCount());
        ChatLog log = database.chatLogs().join().getFirst();
        assertEquals(List.of("alpha", "welcome beta", "gamma"),
            database.entriesAround(log, 1, 1).join().stream().map(ChatEntry::message).toList());
    }

    @Test
    void liveApiIsUnavailableUntilTheClientOpensTheStore() {
        LogDatabase unavailable = LogDatabase.forWorker(null);
        assertFalse(unavailable.isOpen());
        CompletionException error = assertThrows(CompletionException.class,
            () -> unavailable.findEntries(ChatQuery.all()).join());
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("log store is not open", error.getCause().getMessage());
    }

    @Test
    void uninitializedClientReturnsTheSameNotOpenDatabase() {
        LogDatabase live = AllTheLogs.database();
        assertFalse(live.isOpen());
        ExecutionException error = assertThrows(ExecutionException.class,
            () -> live.metadata().get());
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test
    void workerQueriesRunOnceTheStoreIsOpen() {
        try (LogStoreWorker worker = new LogStoreWorker()) {
            LogDatabase closed = LogDatabase.forWorker(worker);
            assertFalse(closed.isOpen());

            Path database = tempDir.resolve("worker.duckdb");
            worker.open(database).join();
            worker.startSession("26.2", null).join();
            LogDatabase open = LogDatabase.forWorker(worker);
            assertTrue(open.isOpen());
            assertEquals(database.toAbsolutePath().normalize(), open.databasePath().join().orElseThrow());
            assertEquals(1, open.chatLogs().join().size());
            assertTrue(open.allEntries().join().isEmpty());
            assertEquals(0, open.countMatches(ChatQuery.all()).join());
        }
    }
}
