package me.wolfii.allthelogs.data.importer.discover;

import me.wolfii.allthelogs.data.ImportPhase;
import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportObserverTest {
    @Test
    void enterPhaseLeavesFileCountsButDropsTheCurrentFile() {
        List<ImportProgress> updates = new ArrayList<>();
        ImportObserver observer = new ImportObserver(updates::add);
        observer.fileStarted(new LogSource.File(Path.of("/tmp/a.log")));
        observer.fileCompleted();
        observer.discoveryFinished();
        observer.finished();
        observer.enterPhase(ImportPhase.CHUNKING, 0d);

        ImportProgress last = updates.getLast();
        assertEquals(ImportPhase.CHUNKING, last.phase());
        assertEquals(0d, last.phaseFraction());
        assertEquals(1, last.completedFiles());
        assertEquals(1, last.discoveredFiles());
        assertTrue(last.discoveryComplete());
        assertNull(last.current());
        assertEquals(ImportProgress.IMPORT_SHARE, last.fraction());
    }

    @Test
    void enterPhaseIgnoresImportSoFileCountsStayTheDriver() {
        List<ImportProgress> updates = new ArrayList<>();
        ImportObserver observer = new ImportObserver(updates::add);
        observer.fileStarted(new LogSource.File(Path.of("/tmp/a.log")));
        observer.enterPhase(ImportPhase.IMPORT, 1d);

        assertEquals(1, updates.size());
        assertEquals(ImportPhase.IMPORT, updates.getLast().phase());
    }
}
