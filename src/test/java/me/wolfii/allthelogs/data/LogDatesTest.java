package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.parse.LogDates;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class LogDatesTest {
    @Test
    void prefersTheDateEncodedInTheFileName() {
        Instant modified = Instant.parse("2026-01-01T00:00:00Z");
        LocalDate resolved = LogDates.resolve("2026-08-24-1.log.gz", modified, ZoneOffset.ofHours(14));

        assertEquals(LocalDate.of(2026, 8, 24), resolved);
    }

    @Test
    void fallsBackToLastModifiedInterpretedInTheGivenTimezone() {
        Instant modified = Instant.parse("2026-08-25T22:00:00Z");

        LocalDate utc = LogDates.resolve("latest.log", modified, ZoneOffset.UTC);
        assertEquals(LocalDate.of(2026, 8, 25), utc);

        LocalDate plusFourteen = LogDates.resolve("latest.log", modified, ZoneOffset.ofHours(14));
        assertEquals(LocalDate.of(2026, 8, 26), plusFourteen);
    }

    @Test
    void conversionToTheSystemTimezoneIsANoOpWhenTheSourceIsLocal() {
        LocalDateTime naive = LocalDateTime.of(2026, 8, 25, 10, 0, 11);
        assertSame(naive, LogDates.toSystemLocal(naive, ZoneId.systemDefault()));
        assertEquals(naive, LogDates.toSystemLocal(LocalDate.of(2026, 8, 25), LocalTime.of(10, 0, 11),
            ZoneId.systemDefault()));
    }

    @Test
    void convertsNaiveTimestampsFromTheSourceTimezoneToLocal() {
        LocalDateTime naive = LocalDateTime.of(2026, 8, 25, 10, 0, 11);
        ZoneOffset source = ZoneOffset.ofHours(-5);
        LocalDateTime expected = naive.atZone(source)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime();

        assertEquals(expected, LogDates.toSystemLocal(naive, source));
        assertEquals(expected, LogDates.toSystemLocal(naive.toLocalDate(), naive.toLocalTime(), source));
    }

    @Test
    void conversionPropagatesNullTimes() {
        assertNull(LogDates.toSystemLocal(null, ZoneOffset.UTC));
        assertNull(LogDates.toSystemLocal(LocalDate.of(2026, 8, 25), null, ZoneOffset.UTC));
    }
}
