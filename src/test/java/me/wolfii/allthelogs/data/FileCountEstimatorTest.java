package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.discover.FileCountEstimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCountEstimatorTest {
    @TempDir
    Path tempDir;

    @Test
    void countsLogsOnDiskAndIgnoresLatestLog() throws IOException {
        Path logs = tempDir.resolve("logs");
        LogFixtures.writeGzipped(logs, "2026-08-24-1.log.gz", LogFixtures.modernLog("26.2", "a"));
        LogFixtures.writePlain(logs, "debug.log", LogFixtures.legacyLog("b"));
        LogFixtures.writePlain(logs, "latest.log", LogFixtures.legacyLog("skip"));
        int estimate = FileCountEstimator.estimateDirectory(tempDir, ImportOptions.defaults());
        assertEquals(2, estimate);
    }

    @Test
    void zipArchivesContributeTheirLogEntries() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "in archive"),
            "logs/2026-01-03-1.log.gz", LogFixtures.modernLog("1.21.8", "second"))));
        assertEquals(2, FileCountEstimator.estimateArchive(archive, ImportOptions.defaults()));
        int directory = FileCountEstimator.estimateDirectory(tempDir, ImportOptions.defaults());
        assertTrue(directory >= 2);
    }
}
