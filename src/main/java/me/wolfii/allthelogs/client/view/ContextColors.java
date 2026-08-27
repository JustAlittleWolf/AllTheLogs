package me.wolfii.allthelogs.client.view;

import java.time.Duration;

/**
 * Greyscale for context lines. Immediate neighbours are medium grey; lines more than 15 minutes from the
 * nearest match reach a darker still-readable grey.
 */
public final class ContextColors {
    public static final int MATCH_TEXT = 0xFFFFFFFF;
    /**
     * Slightly darker than white-green so highlighted substrings stay readable on a dim list.
     */
    public static final int MATCH_HIGHLIGHT = 0xFFA8DC9C;
    public static final int TIMESTAMP = 0xFFA0A0A0;

    static final int NEAR_GRAY = 0xA4;
    static final int FAR_GRAY = 0x72;
    static final long MAX_DISTANCE_MILLIS = Duration.ofMinutes(15).toMillis();

    private ContextColors() {
    }

    public static int contextText(Duration distanceFromMatch) {
        double t = Math.clamp(distanceFromMatch.toMillis() / (double) MAX_DISTANCE_MILLIS, 0, 1);
        int channel = (int) Math.round(NEAR_GRAY + (FAR_GRAY - NEAR_GRAY) * t);
        return 0xFF000000 | (channel << 16) | (channel << 8) | channel;
    }
}
