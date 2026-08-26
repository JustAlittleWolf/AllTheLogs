package me.wolfii.allthelogs.data;

import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
