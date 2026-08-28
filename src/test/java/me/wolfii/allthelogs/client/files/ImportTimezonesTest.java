package me.wolfii.allthelogs.client.files;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ImportTimezonesTest {
    private static final Instant SUMMER = Instant.parse("2026-08-28T12:00:00Z");
    private static final Instant WINTER = Instant.parse("2026-01-15T12:00:00Z");

    @Test
    void importantZonesAreOneCityPerWinterSummerOffsetPair() {
        List<ImportTimezones.Choice> choices = ImportTimezones.important(ZoneOffset.UTC, SUMMER);
        assertTrue(choices.size() > 10);
        Set<String> labels = new HashSet<>();
        for (ImportTimezones.Choice choice : choices) {
            assertTrue(labels.add(choice.city() + choice.offset()), "duplicate " + choice);
            assertTrue(choice.label().contains(choice.city()));
            assertTrue(choice.label().contains("UTC"));
        }
        assertTrue(choices.stream().anyMatch(choice -> "UTC".equals(choice.city())));
        assertTrue(choices.stream().anyMatch(choice -> "New York".equals(choice.city())));
        assertTrue(choices.stream().anyMatch(choice -> "Berlin".equals(choice.city())));
        assertTrue(choices.stream().anyMatch(choice -> "Tokyo".equals(choice.city())));
        assertFalse(choices.stream().anyMatch(choice -> "Paris".equals(choice.city())));
    }

    @Test
    void prefersTheSystemZoneAsTheLabelForItsGroup() {
        List<ImportTimezones.Choice> choices = ImportTimezones.important(ZoneId.of("Europe/Paris"), SUMMER);
        assertTrue(choices.stream().anyMatch(choice -> "Paris".equals(choice.city())));
        assertFalse(choices.stream().anyMatch(choice -> "Berlin".equals(choice.city())));
    }

    @Test
    void matchingFindsCitiesThatLostTheLabelToAPreferredPeer() {
        List<ImportTimezones.Choice> choices = ImportTimezones.important(ZoneOffset.UTC, SUMMER);
        List<ImportTimezones.Choice> matches = ImportTimezones.matching("berlin", choices);
        assertEquals(1, matches.size());
        assertEquals("Berlin", matches.getFirst().city());
        assertTrue(ImportTimezones.matching("tokyo", choices).stream()
            .anyMatch(choice -> "Tokyo".equals(choice.city())));
        assertTrue(ImportTimezones.matching("utc+9", choices).stream()
            .anyMatch(choice -> "Tokyo".equals(choice.city())));
        assertTrue(ImportTimezones.matching("not-a-place", choices).isEmpty());
    }

    @Test
    void parseAcceptsCityNamesIanaIdsAndBlank() {
        List<ImportTimezones.Choice> choices = ImportTimezones.important(ZoneOffset.UTC, SUMMER);
        ZoneId fallback = ZoneId.of("UTC");
        assertEquals(fallback, ImportTimezones.parse("  ", choices, fallback).orElseThrow());
        assertEquals("Europe/Berlin", ImportTimezones.parse("Berlin", choices, fallback).orElseThrow().getId());
        assertEquals("Europe/Berlin",
            ImportTimezones.parse("Europe/Berlin", choices, fallback).orElseThrow().getId());
        assertEquals("America/New_York",
            ImportTimezones.parse("New York", choices, fallback).orElseThrow().getId());
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
