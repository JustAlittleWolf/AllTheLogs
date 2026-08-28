package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.data.ImportPhase;
import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportProgressTextTest {
    @Test
    void currentFileIsRelativeToTheImportPath() {
        Path root = Path.of("/tmp/instance");
        assertEquals("", ImportProgressText.currentFile(null, root));
        assertEquals("logs/latest.log", ImportProgressText.currentFile(
            new LogSource.File(Path.of("/tmp/instance/logs/latest.log")), root));
        assertEquals("backup.zip", ImportProgressText.currentFile(
            new LogSource.Archive(Path.of("/tmp/backup.zip"), ""), Path.of("/tmp/backup.zip")));
        assertEquals("backup.zip!/logs/2026-01-02-1.log.gz", ImportProgressText.currentFile(
            new LogSource.Archive(Path.of("/tmp/backup.zip"), "logs/2026-01-02-1.log.gz"),
            Path.of("/tmp/backup.zip")));
        assertEquals("backups/old.zip!/logs/chat.log", ImportProgressText.currentFile(
            new LogSource.Archive(Path.of("/tmp/instance/backups/old.zip"), "logs/chat.log"), root));
    }

    @Test
    void percentIsTheCompletedFraction() {
        assertEquals(0, ImportProgressText.percent(new ImportProgress(0, 0, 0, false, null)));
        assertEquals(30, ImportProgressText.percent(new ImportProgress(1, 2, 0, true, null)));
        assertEquals(60, ImportProgressText.percent(new ImportProgress(4, 4, 8, true, null)));
        assertEquals(15, ImportProgressText.percent(new ImportProgress(2, 2, 8, false, null)));
        assertEquals(80, ImportProgressText.percent(
            new ImportProgress(4, 4, 4, true, null, ImportPhase.CHUNKING, 1d)));
        assertEquals(100, ImportProgressText.percent(
            new ImportProgress(4, 4, 4, true, null, ImportPhase.OPTIMIZING, 1d)));
    }

    @Test
    void failureReasonUnwrapsWorkerExceptions() {
        Path missing = Path.of("C:\\Users\\Wolfi\\AppData\\Roaming\\.minecraft\\logs");
        LogDataException cause = new LogDataException("not a directory: " + missing);
        assertEquals("not a directory: " + missing, ImportProgressText.failureReason(
            new CompletionException(cause)));
        assertEquals("could not write imported logs", ImportProgressText.failureReason(
            new LogDataException("could not write imported logs", new RuntimeException("disk full"))));
        assertEquals("boom", ImportProgressText.failureReason(new IllegalStateException("boom")));
        assertEquals("IllegalStateException", ImportProgressText.failureReason(new IllegalStateException()));
        assertEquals("", ImportProgressText.failureReason(null));
    }
}
