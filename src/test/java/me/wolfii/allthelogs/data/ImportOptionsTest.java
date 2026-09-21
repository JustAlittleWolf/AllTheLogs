package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ImportOptionsTest {
    @Test
    void rejectsANullTimezone() {
        assertThrows(NullPointerException.class, () -> ImportOptions.defaults().withTimezone((ZoneId) null));
    }

    @Test
    void withTimezoneRejectsAnUnknownIanaName() {
        assertThrows(DateTimeException.class,
            () -> ImportOptions.defaults().withTimezone("Not/AZone"));
    }

    @Test
    void defaultsOptimizeAfterImport() {
        assertTrue(ImportOptions.defaults().optimize());
        assertEquals(0, ImportOptions.defaults().optimizeIfImportedFilesExceed());
        assertTrue(ImportOptions.defaults().shouldOptimize(1));
        assertFalse(ImportOptions.defaults().shouldOptimize(0));
        ImportOptions metadata = ImportOptions.defaults().withUpdateMetadataOnly(true);
        assertFalse(metadata.shouldOptimize(16));
        assertFalse(ImportOptions.defaults().reparseExistingLogs());
        assertTrue(metadata.withSkipAlreadyImported(false).reparseExistingLogs());
        assertFalse(metadata.withSkipAlreadyImported(true).reparseExistingLogs());
    }
}
