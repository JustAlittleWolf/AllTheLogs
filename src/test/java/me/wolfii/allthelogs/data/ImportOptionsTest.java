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
        assertFalse(ImportOptions.currentLogsDirectory().shouldOptimize(15));
        assertTrue(ImportOptions.currentLogsDirectory().shouldOptimize(16));
    }
}
