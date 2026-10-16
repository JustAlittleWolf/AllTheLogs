package me.wolfii.allthelogs.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionMarkerRepeatsTest {
    private static LocalDateTime at(int day, int hour, int minute) {
        return LocalDateTime.of(2024, 5, day, hour, minute);
    }

    @Test
    void repeatsAtMidnightAndTwiceAfterOnlyOnceTheSessionRanTheDayBefore() {
        SessionMarkerRepeats repeats = new SessionMarkerRepeats(at(1, 22, 0));
        assertFalse(repeats.due(at(1, 23, 59)));
        assertTrue(repeats.due(at(2, 0, 0)));
        assertFalse(repeats.due(at(2, 0, 1)));
        assertTrue(repeats.due(at(2, 0, 5)));
        assertFalse(repeats.due(at(2, 0, 20)));
        assertTrue(repeats.due(at(2, 0, 31)));
        assertFalse(repeats.due(at(2, 23, 0)));
        assertTrue(repeats.due(at(3, 0, 0)));
    }

    @Test
    void aSessionStartedAfterMidnightDoesNotRepeatThatNight() {
        SessionMarkerRepeats repeats = new SessionMarkerRepeats(at(2, 0, 2));
        assertFalse(repeats.due(at(2, 0, 5)));
        assertFalse(repeats.due(at(2, 0, 30)));
        assertTrue(repeats.due(at(3, 0, 0)));
    }

    @Test
    void aMissedTimeIsCaughtUpOnTheNextCheck() {
        SessionMarkerRepeats repeats = new SessionMarkerRepeats(at(1, 22, 0));
        assertTrue(repeats.due(at(2, 0, 10)));
        assertFalse(repeats.due(at(2, 0, 11)));
    }
}
