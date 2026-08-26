package me.wolfii.allthelogs.view;

import java.time.Duration;

/**
 * Greyscale for context lines. Immediate neighbours are light grey; lines more than 15 minutes from the
 * nearest match reach a still-readable medium grey.
 */
public final class ContextColors {
    public static final int MATCH_TEXT = 0xFFFFFFFF;
    /**
     * Very light green used as the background behind the matching substring.
     */
    public static final int MATCH_HIGHLIGHT = 0xFFC8F5C0;
    public static final int TIMESTAMP = 0xFFA0A0A0;

    static final int NEAR_GRAY = 0xC8;
    static final int FAR_GRAY = 0x90;
    static final long MAX_DISTANCE_MILLIS = Duration.ofMinutes(15).toMillis();

    private ContextColors() {
    }

    public static int contextText(Duration distanceFromMatch) {
        double t = clamp01(distanceFromMatch.toMillis() / (double) MAX_DISTANCE_MILLIS);
        int channel = (int) Math.round(NEAR_GRAY + (FAR_GRAY - NEAR_GRAY) * t);
        return 0xFF000000 | (channel << 16) | (channel << 8) | channel;
    }

    private static double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }
}
