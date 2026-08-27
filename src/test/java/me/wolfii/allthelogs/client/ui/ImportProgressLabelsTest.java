package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportProgressLabelsTest {
    @Test
    void currentFileIsRelativeToTheImportPath() {
        Path root = Path.of("/tmp/instance");
        assertEquals("", ImportProgressLabels.currentFile(null, root));
        assertEquals("logs/latest.log", ImportProgressLabels.currentFile(
            new LogSource.File(Path.of("/tmp/instance/logs/latest.log")), root));
        assertEquals("backup.zip", ImportProgressLabels.currentFile(
            new LogSource.Archive(Path.of("/tmp/backup.zip"), ""), Path.of("/tmp/backup.zip")));
        assertEquals("backup.zip!/logs/2026-01-02-1.log.gz", ImportProgressLabels.currentFile(
            new LogSource.Archive(Path.of("/tmp/backup.zip"), "logs/2026-01-02-1.log.gz"),
            Path.of("/tmp/backup.zip")));
        assertEquals("backups/old.zip!/logs/chat.log", ImportProgressLabels.currentFile(
            new LogSource.Archive(Path.of("/tmp/instance/backups/old.zip"), "logs/chat.log"), root));
    }

    @Test
    void percentIsTheCompletedFraction() {
        assertEquals(0, ImportProgressLabels.percent(new ImportProgress(0, 0, 0, false, null)));
        assertEquals(50, ImportProgressLabels.percent(new ImportProgress(1, 2, 0, true, null)));
        assertEquals(100, ImportProgressLabels.percent(new ImportProgress(4, 4, 8, true, null)));
        assertEquals(25, ImportProgressLabels.percent(new ImportProgress(2, 2, 8, false, null)));
    }
}
