package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.importer.discover.FileCountEstimator;
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

    @Test
    void directoryEstimateAppliesThePathMatcherUpToZipFiles() throws IOException {
        LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
            "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "from root zip"))));
        LogFixtures.writeZip(tempDir.resolve("logs/old.zip"), new LinkedHashMap<>(Map.of(
            "2026-01-04-1.log.gz", LogFixtures.modernLog("1.21.8", "from logs zip"))));
        LogFixtures.writeGzipped(tempDir.resolve("logs"), "2026-08-26-1.log.gz",
            LogFixtures.modernLog("26.2", "from logs folder"));
        LogFixtures.writePlain(tempDir.resolve("crash-reports"), "crash.log",
            LogFixtures.modernLog("26.2", "crash"));

        int estimate = FileCountEstimator.estimateDirectory(tempDir,
            ImportOptions.defaults().withPathMatcher("**/logs/**"));
        assertEquals(2, estimate);
    }
}
