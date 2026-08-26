package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportProgressLabelsTest {
    @Test
    void currentFileUsesTheLeafName() {
        assertEquals("", ImportProgressLabels.currentFile(null));
        assertEquals("latest.log", ImportProgressLabels.currentFile(
            new LogSource.File(Path.of("/tmp/logs/latest.log"))));
        assertEquals("backup.zip", ImportProgressLabels.currentFile(
            new LogSource.Archive(Path.of("/tmp/backup.zip"), "")));
        assertEquals("logs/2026-01-02-1.log.gz", ImportProgressLabels.currentFile(
            new LogSource.Archive(Path.of("/tmp/backup.zip"), "logs/2026-01-02-1.log.gz")));
    }

    @Test
    void percentIsTheCompletedFraction() {
        assertEquals(0, ImportProgressLabels.percent(new ImportProgress(0, 0, 0, false, null)));
        assertEquals(50, ImportProgressLabels.percent(new ImportProgress(1, 2, 0, true, null)));
        assertEquals(100, ImportProgressLabels.percent(new ImportProgress(4, 4, 8, true, null)));
        assertEquals(25, ImportProgressLabels.percent(new ImportProgress(2, 2, 8, false, null)));
    }
}
