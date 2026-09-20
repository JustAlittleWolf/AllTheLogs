package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoreAnalyticsTest {
    @BeforeEach
    @AfterEach
    void reset() {
        StoreAnalytics.INSTANCE.clear();
        StoreAnalytics.INSTANCE.setLog(ignored -> {
        });
    }

    @Test
    void recordsQueueWaitSeparatelyFromRunTime() throws Exception {
        StoreAnalytics.Job first = StoreAnalytics.INSTANCE.submit("slow");
        StoreAnalytics.Job second = StoreAnalytics.INSTANCE.submit("queued");
        assertEquals(2, StoreAnalytics.INSTANCE.outstanding());

        first.run(() -> Thread.sleep(40));
        second.run(() -> {
        });

        List<StoreAnalytics.Sample> recent = StoreAnalytics.INSTANCE.recent();
        assertEquals(2, recent.size());
        assertEquals("slow", recent.get(0).name());
        assertEquals("queued", recent.get(1).name());
        assertTrue(recent.get(1).waitNanos() >= 20_000_000L,
            "second job should have waited for the first, wait=" + recent.get(1).waitNanos());
        assertEquals(1, recent.get(1).queueDepth());
        assertTrue(StoreAnalytics.INSTANCE.report().getFirst().contains("idle"));
        assertTrue(StoreAnalytics.INSTANCE.totals().get("slow").runNanos() >= 20_000_000L);
    }

    @Test
    void nestedMeasuresShowInnerDuckDbWork() throws Exception {
        StoreAnalytics.INSTANCE.measure("importDirectory", () -> {
            StoreAnalytics.INSTANCE.measure("clusterEntries", () -> {
                Thread.sleep(15);
                return null;
            });
            return null;
        });
        List<String> report = StoreAnalytics.INSTANCE.report();
        assertTrue(report.stream().anyMatch(line -> line.startsWith("clusterEntries")), report::toString);
        assertTrue(report.stream().anyMatch(line -> line.startsWith("importDirectory")), report::toString);
        assertTrue(report.stream().anyMatch(line -> line.startsWith("total clusterEntries")), report::toString);
    }

    @Test
    void quietOpsAreOmittedUnlessTheyAreSlow() throws Exception {
        StoreAnalytics.INSTANCE.measureQuiet("liveChat", () -> null);
        assertTrue(StoreAnalytics.INSTANCE.recent().isEmpty());
        StoreAnalytics.INSTANCE.measureQuiet("liveChat", () -> {
            Thread.sleep(8);
            return null;
        });
        assertEquals(1, StoreAnalytics.INSTANCE.recent().size());
        assertEquals("liveChat", StoreAnalytics.INSTANCE.recent().getFirst().name());
    }

    @Test
    void logsSamplesOverTheThreshold() throws Exception {
        List<String> logged = new ArrayList<>();
        StoreAnalytics.INSTANCE.setLog(logged::add);
        StoreAnalytics.INSTANCE.measure("compact", () -> {
            Thread.sleep(15);
            return null;
        });
        assertEquals(1, logged.size());
        assertTrue(logged.getFirst().contains("[store-perf] compact"), logged::toString);
    }

    @Test
    void jobRunReleasesTheOutstandingCountWhenTheWorkFails() {
        StoreAnalytics.Job job = StoreAnalytics.INSTANCE.submit("broken");
        assertThrows(IllegalStateException.class, () -> job.run(() -> {
            throw new IllegalStateException("nope");
        }));
        assertEquals(0, StoreAnalytics.INSTANCE.outstanding());
        assertEquals("idle", StoreAnalytics.INSTANCE.current());
    }

    @Test
    void historyKeepsOnlyTheLatestSamples() throws Exception {
        for (int i = 0; i < StoreAnalytics.HISTORY + 5; i++) {
            StoreAnalytics.INSTANCE.measure("n" + i, () -> null);
        }
        assertEquals(StoreAnalytics.HISTORY, StoreAnalytics.INSTANCE.recent().size());
        assertEquals("n" + (StoreAnalytics.HISTORY + 4), StoreAnalytics.INSTANCE.recent().getLast().name());
    }

    @Test
    void workerRecordsNamedOpenAndMetadataJobs() throws Exception {
        java.nio.file.Path database = java.nio.file.Files.createTempDirectory("perf").resolve("logs.duckdb");
        try (me.wolfii.allthelogs.client.LogStoreWorker worker = new me.wolfii.allthelogs.client.LogStoreWorker()) {
            worker.open(database).join();
            worker.browserMetadata().join();
        }
        assertTrue(StoreAnalytics.INSTANCE.totals().containsKey("open"), StoreAnalytics.INSTANCE.totals()::toString);
        assertTrue(StoreAnalytics.INSTANCE.totals().containsKey("browserMetadata"),
            StoreAnalytics.INSTANCE.totals()::toString);
        assertTrue(StoreAnalytics.INSTANCE.totals().containsKey("metadata.logFileStats"),
            StoreAnalytics.INSTANCE.report()::toString);
    }
}
