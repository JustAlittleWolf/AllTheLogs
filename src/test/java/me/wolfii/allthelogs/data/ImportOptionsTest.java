package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ImportOptionsTest {
    @Test
    void defaultsToTheSystemTimezone() {
        assertEquals(ZoneId.systemDefault(), ImportOptions.defaults().timezone());
    }

    @Test
    void withTimezoneReplacesOnlyTheTimezone() {
        ImportOptions original = ImportOptions.defaults()
            .withRecursive(false)
            .withNestedArchives(false)
            .withPathMatcher("**/logs/**")
            .withParallelism(2)
            .withSkipAlreadyImported(true);

        ImportOptions updated = original.withTimezone(ZoneOffset.UTC);

        assertEquals(ZoneOffset.UTC, updated.timezone());
        assertEquals(original.recursive(), updated.recursive());
        assertEquals(original.nestedArchives(), updated.nestedArchives());
        assertEquals(original.pathMatcher(), updated.pathMatcher());
        assertEquals(original.parallelism(), updated.parallelism());
        assertEquals(original.skipAlreadyImported(), updated.skipAlreadyImported());
        assertEquals(original.optimize(), updated.optimize());
        assertEquals(original.optimizeIfImportedFilesExceed(), updated.optimizeIfImportedFilesExceed());
    }

    @Test
    void rejectsANullTimezone() {
        assertThrows(NullPointerException.class, () -> ImportOptions.defaults().withTimezone((ZoneId) null));
    }

    @Test
    void withTimezoneSystemDefaultLeavesTheDefaultUnchanged() {
        ZoneId local = ZoneId.systemDefault();
        assertSame(local, ImportOptions.defaults().withTimezone(local).timezone());
    }

    @Test
    void withTimezoneRejectsAnUnknownIanaName() {
        assertThrows(DateTimeException.class,
            () -> ImportOptions.defaults().withTimezone("Not/AZone"));
    }

    @Test
    void currentLogsDirectoryLooksInsideTheLogsFolderRecursivelyWithoutOpeningArchives() {
        ImportOptions options = ImportOptions.currentLogsDirectory();
        assertTrue(options.recursive());
        assertFalse(options.nestedArchives());
        assertTrue(options.skipAlreadyImported());
        assertEquals(ImportOptions.LOGS_DIRECTORY_MATCHER, options.pathMatcher());
        assertFalse(options.optimize());
        assertEquals(ImportOptions.STARTUP_OPTIMIZE_AFTER_MORE_THAN, options.optimizeIfImportedFilesExceed());
    }

    @Test
    void currentGameDirectoryStaysInsideLogsFoldersAndSkipsZips() {
        ImportOptions options = ImportOptions.currentGameDirectory();
        assertTrue(options.recursive());
        assertFalse(options.nestedArchives());
        assertTrue(options.skipAlreadyImported());
        assertEquals(ImportOptions.GAME_DIRECTORY_MATCHER, options.pathMatcher());
        assertFalse(options.optimize());
        assertEquals(ImportOptions.STARTUP_OPTIMIZE_AFTER_MORE_THAN, options.optimizeIfImportedFilesExceed());
    }

    @Test
    void defaultsOptimizeAfterImport() {
        assertTrue(ImportOptions.defaults().optimize());
        assertEquals(0, ImportOptions.defaults().optimizeIfImportedFilesExceed());
        assertTrue(ImportOptions.defaults().shouldOptimize(1));
        assertFalse(ImportOptions.currentLogsDirectory().shouldOptimize(15));
        assertTrue(ImportOptions.currentLogsDirectory().shouldOptimize(16));
    }

    @Test
    void withOptimizeReplacesOnlyTheOptimizeFlag() {
        ImportOptions original = ImportOptions.defaults()
            .withRecursive(false)
            .withNestedArchives(false)
            .withPathMatcher("**/logs/**")
            .withParallelism(2)
            .withSkipAlreadyImported(true);

        ImportOptions skipped = original.withOptimize(false);

        assertFalse(skipped.optimize());
        assertTrue(original.optimize());
        assertEquals(original.recursive(), skipped.recursive());
        assertEquals(original.nestedArchives(), skipped.nestedArchives());
        assertEquals(original.pathMatcher(), skipped.pathMatcher());
        assertEquals(original.parallelism(), skipped.parallelism());
        assertEquals(original.skipAlreadyImported(), skipped.skipAlreadyImported());
        assertEquals(original.optimizeIfImportedFilesExceed(), skipped.optimizeIfImportedFilesExceed());
        assertEquals(original.timezone(), skipped.timezone());
    }
}
