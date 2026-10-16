package me.wolfii.allthelogs.client.playtime;

/**
 * Stable color for a version, server, or version-and-server pair. The same key keeps its color when the
 * filter or the zoom changes.
 */
public final class PlaytimeColors {
    private PlaytimeColors() {
    }

    public static int of(String version, String serverKey) {
        int hash = 17;
        hash = hash * 31 + (version == null ? 0 : version.hashCode());
        hash = hash * 31 + (serverKey == null ? 0 : serverKey.hashCode());
        float hue = (hash & 0xFFFF) / 65535f;
        return hsb(hue, 0.55f, 0.92f);
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
