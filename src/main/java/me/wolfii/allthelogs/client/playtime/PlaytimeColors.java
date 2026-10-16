package me.wolfii.allthelogs.client.playtime;

/**
 * Series colors. Each series gets a rank from when it first appears in the whole history, and the rank picks
 * a color. Neighboring ranks are far apart in hue and alternate in brightness, so series that were played
 * around the same time stay easy to tell apart. New history is appended later in time, so existing series
 * keep their color when the filter, the zoom, or the import changes.
 */
public final class PlaytimeColors {
    /** Series with neither a known version nor a known server. */
    public static final int UNKNOWN = 0xFF8A8A8A;
    private static final double GOLDEN = 0.6180339887498949;
    private static final float[][] TIERS = {
        {0.62f, 0.95f},
        {0.78f, 0.76f},
        {0.38f, 0.86f},
    };

    private PlaytimeColors() {
    }

    public static int ofRank(int rank) {
        double hue = (0.08 + rank * GOLDEN) % 1.0;
        float[] tier = TIERS[Math.floorMod(rank, TIERS.length)];
        return hsb((float) hue, tier[0], tier[1]);
    }

    /**
     * {@link #ofRank} with little saturation, for series that are only partly known.
     */
    public static int ofRankMuted(int rank) {
        double hue = (0.08 + rank * GOLDEN) % 1.0;
        return hsb((float) hue, 0.38f, 0.8f);
    }

    /**
     * Each group of three ranks shares a pattern, and neighboring groups differ. Ranks three apart are the
     * closest in hue within a group of neighbors, so this puts them in different patterns.
     */
    public static PlaytimePattern pattern(int rank) {
        PlaytimePattern[] patterns = PlaytimePattern.values();
        return patterns[Math.floorMod(Math.floorDiv(rank, 3), patterns.length)];
    }

    /**
     * {@code ARGB} with each channel scaled toward black, used to dim the series that are not highlighted.
     */
    public static int dim(int color) {
        int red = ((color >> 16) & 0xFF) * 3 / 10;
        int green = ((color >> 8) & 0xFF) * 3 / 10;
        int blue = (color & 0xFF) * 3 / 10;
        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Pattern color for a section: the fill color at about 78% brightness, close enough to stay calm.
     */
    public static int stripe(int color) {
        int red = ((color >> 16) & 0xFF) * 78 / 100;
        int green = ((color >> 8) & 0xFF) * 78 / 100;
        int blue = (color & 0xFF) * 78 / 100;
        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    static int hsb(float hue, float saturation, float brightness) {
        float sector = (hue - (float) Math.floor(hue)) * 6f;
        int index = (int) sector;
        float fraction = sector - index;
        float peak = brightness;
        float floor = brightness * (1f - saturation);
        float falling = brightness * (1f - saturation * fraction);
        float rising = brightness * (1f - saturation * (1f - fraction));
        float red;
        float green;
        float blue;
        switch (index) {
            case 0 -> { red = peak; green = rising; blue = floor; }
            case 1 -> { red = falling; green = peak; blue = floor; }
            case 2 -> { red = floor; green = peak; blue = rising; }
            case 3 -> { red = floor; green = falling; blue = peak; }
            case 4 -> { red = rising; green = floor; blue = peak; }
            default -> { red = peak; green = floor; blue = falling; }
        }
        return 0xFF000000
            | (channel(red) << 16)
            | (channel(green) << 8)
            | channel(blue);
    }

    private static int channel(float value) {
        return Math.min(255, Math.max(0, Math.round(value * 255f)));
    }
}
