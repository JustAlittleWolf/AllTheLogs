package me.wolfii.allthelogs.data.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StoreCancellationTest {
    @TempDir
    Path tempDir;

    @Test
    void stoppingBeforeTheBackupLeavesTheDatabaseUntouched() throws Exception {
        Path database = tempDir.resolve("logs.duckdb");
        byte[] original = new byte[64 * 1024];
        new Random(1).nextBytes(original);
        Files.write(database, original);
        StoreCancellation cancellation = StoreCancellation.create();
        cancellation.requestStop();
        StoreCancellation.install(cancellation);
        try {
            IOException error = assertThrows(IOException.class,
                () -> DatabaseBackup.create(database, 5, Instant.parse("2026-09-27T18:00:00Z")));
            assertTrue(StoreCancellation.isClosedRequest(error));
        } finally {
            StoreCancellation.clear();
        }
        assertArrayEquals(original, Files.readAllBytes(database));
        assertTrue(DatabaseBackup.list(database).isEmpty());
        try (var children = Files.list(tempDir)) {
            assertEquals(1, children.count(), "a cancelled backup must not leave a partial archive");
        }
    }

    @Test
    void cancellingTheActiveStatementStopsTheQueryAndKeepsCommittedRows() throws Exception {
        StoreCancellation cancellation = StoreCancellation.create();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE kept (id INTEGER)");
            statement.execute("INSERT INTO kept VALUES (1)");
            Thread worker = new Thread(() -> {
                StoreCancellation.install(cancellation);
                try {
                    cancellation.bind(statement);
                    started.countDown();
                    statement.execute("SELECT sum(random()) FROM range(1000000000)");
                    failure.set(new AssertionError("query should have been cancelled"));
                } catch (SQLException e) {
                    if (!StoreCancellation.isClosedRequest(e)) failure.set(e);
                } finally {
                    StoreCancellation.clear();
                }
            }, "cancel-test");
            worker.start();
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Thread.sleep(300);
            long began = System.nanoTime();
            cancellation.requestStop();
            worker.interrupt();
            worker.join(10_000);
            assertFalse(worker.isAlive(), "the store thread should leave the cancelled query");
            assertTrue((System.nanoTime() - began) < 10_000_000_000L);
            assertNull(failure.get());
            try (Statement read = connection.createStatement();
                 ResultSet result = read.executeQuery("SELECT id FROM kept")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void aCancelledUpdateDoesNotKeepUncommittedRowChanges() throws Exception {
        StoreCancellation cancellation = StoreCancellation.create();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Path database = tempDir.resolve("logs.duckdb");
        try (var connection = StoreConnections.openFile(database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample (id INTEGER, label VARCHAR)");
            statement.execute("INSERT INTO sample SELECT i, 'old' FROM range(1000) t(i)");
            Thread worker = new Thread(() -> {
                StoreCancellation.install(cancellation);
                try (Statement update = connection.createStatement()) {
                    cancellation.bind(update);
                    started.countDown();
                    update.execute("""
                        UPDATE sample SET label = 'new'
                        WHERE (SELECT sum(random()) FROM range(1000000000)) >= 0""");
                    failure.set(new AssertionError("update should have been cancelled"));
                } catch (SQLException e) {
                    if (!StoreCancellation.isClosedRequest(e)) failure.set(e);
                } finally {
                    StoreCancellation.clear();
                }
            }, "update-cancel-test");
            worker.start();
            assertTrue(started.await(10, TimeUnit.SECONDS));
            Thread.sleep(300);
            cancellation.requestStop();
            worker.interrupt();
            worker.join(10_000);
            assertFalse(worker.isAlive());
            assertNull(failure.get());
            try (ResultSet result = statement.executeQuery(
                "SELECT count(*) FROM sample WHERE label = 'new'")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1), "a cancelled update must not change stored rows");
            }
            try (ResultSet result = statement.executeQuery("SELECT count(*) FROM sample")) {
                assertTrue(result.next());
                assertEquals(1000, result.getLong(1));
            }
        }
    }
}
