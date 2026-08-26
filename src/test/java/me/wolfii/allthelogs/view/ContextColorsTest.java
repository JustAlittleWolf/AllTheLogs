package me.wolfii.allthelogs.view;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextColorsTest {
    @Test
    void nearbyContextIsLighterThanDistantContext() {
        int near = ContextColors.contextText(Duration.ZERO);
        int mid = ContextColors.contextText(Duration.ofMinutes(7));
        int far = ContextColors.contextText(Duration.ofMinutes(15));
        int beyond = ContextColors.contextText(Duration.ofHours(3));

        assertTrue(channel(near) > channel(mid));
        assertTrue(channel(mid) > channel(far));
        assertEquals(far, beyond);
        assertEquals(0xFFC8C8C8, near);
        assertEquals(0xFF909090, far);
    }

    @Test
    void highlightIsAVeryLightGreen() {
        assertEquals(0xFFC8F5C0, ContextColors.MATCH_HIGHLIGHT);
        assertEquals(0xFFFFFFFF, ContextColors.MATCH_TEXT);
    }

    private static int channel(int argb) {
        return argb & 0xFF;
    }
}
