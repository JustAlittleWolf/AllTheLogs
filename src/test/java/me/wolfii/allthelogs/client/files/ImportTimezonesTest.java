package me.wolfii.allthelogs.client.files;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportTimezonesTest {
    private static final Instant SUMMER = Instant.parse("2026-08-28T12:00:00Z");
    private static final Instant WINTER = Instant.parse("2026-01-15T12:00:00Z");

    @Test
    void listsEveryIanaZoneWithItsFullIdInTheLabel() {
        List<ImportTimezones.Choice> choices = ImportTimezones.buildChoices(SUMMER);
        assertTrue(choices.size() > 400);
        assertTrue(choices.stream().anyMatch(choice -> "Europe/Vienna".equals(choice.zone().getId())));
        assertTrue(choices.stream().anyMatch(choice -> "Europe/Paris".equals(choice.zone().getId())));
        assertTrue(choices.stream().anyMatch(choice -> "Europe/Berlin".equals(choice.zone().getId())));
        for (ImportTimezones.Choice choice : choices) {
            assertTrue(choice.label().startsWith(choice.zone().getId() + " ("));
            assertTrue(choice.label().contains("UTC"));
        }
    }

    @Test
    void blankMatchingShowsNothing() {
        List<ImportTimezones.Choice> choices = ImportTimezones.buildChoices(SUMMER);
        assertTrue(ImportTimezones.matching("", choices).isEmpty());
        assertTrue(ImportTimezones.matching("   ", choices).isEmpty());
    }

    @Test
    void matchingFindsZonesByIdCityOrOffset() {
        List<ImportTimezones.Choice> choices = ImportTimezones.buildChoices(SUMMER);
        assertEquals("Europe/Vienna",
            ImportTimezones.matching("vienna", choices).getFirst().zone().getId());
        assertEquals("Europe/Berlin",
            ImportTimezones.matching("europe/berlin", choices).getFirst().zone().getId());
        assertTrue(ImportTimezones.matching("utc+9", choices).stream()
            .anyMatch(choice -> "Asia/Tokyo".equals(choice.zone().getId())));
        assertTrue(ImportTimezones.matching("not-a-place", choices).isEmpty());
    }

    @Test
    void parseAcceptsFullLabelsIanaIdsAndBlank() {
        List<ImportTimezones.Choice> choices = ImportTimezones.buildChoices(SUMMER);
        ZoneId fallback = ZoneId.of("UTC");
        assertEquals(fallback, ImportTimezones.parse("  ", choices, fallback).orElseThrow());
        assertEquals("Europe/Vienna",
            ImportTimezones.parse("Europe/Vienna", choices, fallback).orElseThrow().getId());
        assertEquals("Europe/Vienna",
            ImportTimezones.parse("Europe/Vienna (UTC+02:00)", choices, fallback).orElseThrow().getId());
        assertEquals("America/New_York",
            ImportTimezones.parse("America/New_York", choices, fallback).orElseThrow().getId());
        assertTrue(ImportTimezones.parse("definitely-not-a-zone", choices, fallback).isEmpty());
    }

    @Test
    void summerTimeFollowsTheLogDateWhenEnabled() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        assertTrue(ImportTimezones.observesDaylightSaving(berlin, SUMMER));
        assertFalse(ImportTimezones.observesDaylightSaving(ZoneOffset.UTC, SUMMER));
        assertFalse(ImportTimezones.observesDaylightSaving(ZoneId.of("Asia/Tokyo"), SUMMER));

        assertEquals(berlin, ImportTimezones.forImport(berlin, true, SUMMER));
        ZoneId winterOffset = ImportTimezones.forImport(berlin, false, SUMMER);
        assertEquals(ZoneOffset.ofHours(1), winterOffset);
        assertTrue(ImportTimezones.isDaylightSaving(berlin, SUMMER));
        assertFalse(ImportTimezones.isDaylightSaving(berlin, WINTER));
    }

    @Test
    void formatOffsetAndCityName() {
        assertEquals("UTC", ImportTimezones.formatOffset(ZoneOffset.UTC));
        assertEquals("UTC+02:00", ImportTimezones.formatOffset(ZoneOffset.ofHours(2)));
        assertEquals("UTC-05:00", ImportTimezones.formatOffset(ZoneOffset.ofHours(-5)));
        assertEquals("New York", ImportTimezones.cityName(ZoneId.of("America/New_York")));
        assertEquals("UTC", ImportTimezones.cityName(ZoneOffset.UTC));
    }
}
