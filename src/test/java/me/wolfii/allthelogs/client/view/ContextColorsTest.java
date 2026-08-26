package me.wolfii.allthelogs.client.view;

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
        assertEquals(0xFFA0A0A0, near);
        assertEquals(0xFF727272, far);
    }

    @Test
    void highlightIsASlightlyDarkerGreen() {
        assertEquals(0xFFA8DC9C, ContextColors.MATCH_HIGHLIGHT);
        assertEquals(0xFFFFFFFF, ContextColors.MATCH_TEXT);
    }

    private static int channel(int argb) {
        return argb & 0xFF;
    }
}
