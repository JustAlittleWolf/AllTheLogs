package me.wolfii.allthelogs.client.ui.theme;

/**
 * Palette for the log browser: match vs context text, hover cards, and store-info tooltips.
 */
public final class Colors {
    public static final int MATCH_TEXT = 0xFFFFFFFF;
    /**
     * Marker fill for search hits: the previous highlight green at 33% opacity.
     */
    public static final int MATCH_HIGHLIGHT = 0x54A8DC9C;
    public static final int TIMESTAMP = 0xFFA0A0A0;
    /**
     * Timestamp on context lines, a step darker than {@link #TIMESTAMP}.
     */
    public static final int CONTEXT_TIMESTAMP = 0xFF7E7E7E;
    /**
     * Multiply layer that darkens context lines relative to matches.
     */
    public static final int CONTEXT_TEXT = 0xFFA4A4A4;
    public static final int INFO_DATE = 0xFFD8D8D8;
    public static final int INFO_VERSION = 0xFF7EB8D4;
    public static final int INFO_FILE = 0xFFB8A9E0;
    /**
     * Multiply layer for literal {@code \n} tokens that were turned into visual linebreaks.
     */
    public static final int ESCAPE_TEXT = 0xFF6E6E6E;
    public static final int META_LABEL = 0xFF9A9A9A;
    public static final int META_VALUE = 0xFFD8D8D8;
    public static final int META_NUMBER = 0xFF7EB8D4;
    public static final int META_SIZE = 0xFFA8C48A;

    private Colors() {
    }

    /**
     * Channel-wise multiply of two ARGB colours, the same stacking Minecraft uses for dyes. White is a no-op.
     */
    public static int multiply(int left, int right) {
        int a = ((left >>> 24) * (right >>> 24) + 127) / 255;
        int r = (((left >> 16) & 0xFF) * ((right >> 16) & 0xFF) + 127) / 255;
        int g = (((left >> 8) & 0xFF) * ((right >> 8) & 0xFF) + 127) / 255;
        int b = ((left & 0xFF) * (right & 0xFF) + 127) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Starts from {@code base} (the chat colour, or white) and multiplies each overlay in order.
     */
    public static int stack(int base, int... overlays) {
        int color = base;
        if (overlays == null) return color;
        for (int overlay : overlays) {
            color = multiply(color, overlay);
        }
        return color;
    }
}
