package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportProgressTest {
    @TempDir
    Path tempDir;

    private LogStore store;

    @BeforeEach
    void setUp() {
        store = LogStore.openInMemory();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private Path logsDirectory() throws IOException {
        Path logs = tempDir.resolve("instance/logs");
        LogFixtures.writeGzipped(logs, "2026-08-24-1.log.gz",
                LogFixtures.modernLog("26.2", "alpha", "beta", "gamma"));
        LogFixtures.writeGzipped(logs, "2026-08-25-1.log.gz",
                LogFixtures.modernLog("26.2", "delta", "needle in here", "epsilon"));
        LogFixtures.writePlain(logs, "latest.log", LogFixtures.legacyLog("zeta", "another needle"));
        return tempDir.resolve("instance");
    }

    @Test
    void reportsEachDirectoryLogAsTheCurrentFile() throws IOException {
        Path root = logsDirectory();
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        ImportResult result = store.importDirectory(root, updates::add);

        assertEquals(3, result.importedFiles());
        assertFalse(updates.isEmpty());
        ImportProgress last = updates.getLast();
        assertTrue(last.discoveryComplete());
        assertEquals(3, last.discoveredFiles());
        assertEquals(3, last.completedFiles());
        assertEquals(1.0, last.fraction());
        assertNull(last.current());

        Set<Path> seenFiles = updates.stream()
                .map(ImportProgress::current)
                .filter(LogSource.File.class::isInstance)
                .map(source -> ((LogSource.File) source).path())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                root.resolve("logs/2026-08-24-1.log.gz").toAbsolutePath().normalize(),
                root.resolve("logs/2026-08-25-1.log.gz").toAbsolutePath().normalize(),
                root.resolve("logs/latest.log").toAbsolutePath().normalize()), seenFiles);

        assertTrue(updates.stream().map(ImportProgress::current).filter(Objects::nonNull)
                .noneMatch(LogSource.Archive.class::isInstance));
        assertTrue(updates.stream().allMatch(progress -> progress.completedFiles() <= progress.discoveredFiles()));
    }

    @Test
    void reportsTheArchiveBeingReadAndTheLogInsideIt() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("backup.zip"), new LinkedHashMap<>(Map.of(
                "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "in archive"))));
        Path absoluteArchive = archive.toAbsolutePath().normalize();
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        store.importArchive(archive, updates::add);

        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absoluteArchive)
                        && a.entryPath().isEmpty()));
        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absoluteArchive)
                        && a.entryPath().equals("logs/2026-01-02-1.log.gz")));

        ImportProgress last = updates.getLast();
        assertTrue(last.discoveryComplete());
        assertEquals(1, last.discoveredFiles());
        assertEquals(1, last.completedFiles());
        assertNull(last.current());
    }

    @Test
    void reportsANestedArchiveAsTheCurrentItem() throws IOException {
        byte[] inner = LogFixtures.zipBytes(Map.of("logs/2026-02-03-1.log", LogFixtures.legacyLog("deeply nested")));
        Path outer = tempDir.resolve("outer.zip");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(outer))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("instances/inner.zip"));
            zip.write(inner);
            zip.closeEntry();
        }
        Path absoluteOuter = outer.toAbsolutePath().normalize();
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        store.importArchive(outer, updates::add);

        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absoluteOuter)
                        && a.entryPath().equals("instances/inner.zip")));
        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absoluteOuter)
                        && a.entryPath().equals("instances/inner.zip!/logs/2026-02-03-1.log")));
    }

    @Test
    void directoryImportReportsArchivesFoundOnTheWay() throws IOException {
        Path archive = LogFixtures.writeZip(tempDir.resolve("instance/backup.zip"), new LinkedHashMap<>(Map.of(
                "logs/2026-01-02-1.log.gz", LogFixtures.modernLog("1.21.8", "from nested zip"))));
        Path absoluteArchive = archive.toAbsolutePath().normalize();
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        store.importDirectory(tempDir, updates::add);

        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absoluteArchive)
                        && a.entryPath().isEmpty()));
        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absoluteArchive)
                        && a.entryPath().equals("logs/2026-01-02-1.log.gz")));
    }

    @Test
    void skippedFilesCountAsCompletedProgress() throws IOException {
        Path root = logsDirectory();
        store.importDirectory(root);
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        ImportResult result = store.importDirectory(root,
                ImportOptions.defaults().withSkipAlreadyImported(true), updates::add);

        assertEquals(3, result.skippedFiles());
        ImportProgress last = updates.getLast();
        assertEquals(3, last.discoveredFiles());
        assertEquals(3, last.completedFiles());
        assertTrue(last.discoveryComplete());
    }

    @Test
    void emptyFilesCountAsCompletedProgress() throws IOException {
        LogFixtures.writePlain(tempDir.resolve("logs"), "2026-04-01-1.log",
                "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        store.importDirectory(tempDir, updates::add);

        ImportProgress last = updates.getLast();
        assertEquals(1, last.discoveredFiles());
        assertEquals(1, last.completedFiles());
        assertTrue(last.discoveryComplete());
    }

    @Test
    void unreadableArchivesStillReportTheArchiveAsCurrent() throws IOException {
        Path broken = tempDir.resolve("broken.zip");
        Files.write(broken, "definitely not a zip".getBytes());
        Path absolute = broken.toAbsolutePath().normalize();
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        store.importArchive(broken, updates::add);

        assertTrue(updates.stream().map(ImportProgress::current)
                .anyMatch(source -> source instanceof LogSource.Archive a
                        && a.path().equals(absolute)
                        && a.entryPath().isEmpty()));
        ImportProgress last = updates.getLast();
        assertTrue(last.discoveryComplete());
        assertEquals(0, last.discoveredFiles());
        assertEquals(0, last.completedFiles());
    }

    @Test
    void progressCountsStayConsistentWhenImportingInParallel() throws IOException {
        Path logs = tempDir.resolve("logs");
        for (int day = 1; day <= 28; day++) {
            LogFixtures.writeGzipped(logs, String.format("2026-05-%02d-1.log.gz", day),
                    LogFixtures.modernLog("26.2", "day " + day + " message", "filler"));
        }
        List<ImportProgress> updates = new CopyOnWriteArrayList<>();

        ImportResult result = store.importDirectory(tempDir, ImportOptions.defaults().withParallelism(8),
                updates::add);

        assertEquals(28, result.importedFiles());
        for (ImportProgress progress : updates) {
            assertTrue(progress.completedFiles() <= progress.discoveredFiles());
            assertTrue(progress.fraction() >= 0);
            assertTrue(progress.fraction() <= 1);
        }
        ImportProgress last = updates.getLast();
        assertEquals(28, last.completedFiles());
        assertEquals(28, last.discoveredFiles());
        assertTrue(last.discoveryComplete());
        assertNull(last.current());
    }

    @Test
    void fractionIsZeroWhenNothingHasBeenDiscovered() {
        assertEquals(0d, new ImportProgress(0, 0, false, null).fraction());
        assertEquals(0.5, new ImportProgress(1, 2, false, null).fraction());
    }
}
