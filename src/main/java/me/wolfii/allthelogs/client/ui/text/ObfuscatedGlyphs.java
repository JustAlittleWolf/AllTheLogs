package me.wolfii.allthelogs.client.ui.text;

import java.util.function.IntUnaryOperator;

/**
 * Same-advance replacements for {@code §k} that are still real characters.
 * <p>
 * Minecraft 26.2 prepares GUI text and then drops the whole string when the prepared bounds are
 * null. {@code Style.obfuscated} picks a random glyph at prepare time; those often have no drawable
 * glyph, so a row that left the viewport can vanish when it is scrolled back. Drawing an actual
 * character of the same width keeps the scramble without that cull.
 */
public final class ObfuscatedGlyphs {
    /**
     * Printable ASCII used as replacements. Spaces and newlines are left alone so wrap and
     * selection stay lined up with the stored message.
     */
    static final String POOL = "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        + "[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

    private ObfuscatedGlyphs() {
    }

    /**
     * Frame index for the scramble. Minecraft's own obfuscation rerolls every frame; this is close
     * enough to look alive without depending on a client tick counter.
     */
    public static long tick() {
        return System.currentTimeMillis() / 50;
    }

    /**
     * Replaces each non-space character with a different glyph of the same {@code widthOfCodePoint}
     * advance. Falls back to the original character when the pool has no match, so the row always
     * stays drawable.
     */
    public static String scramble(String text, int baseIndex, long tick, IntUnaryOperator widthOfCodePoint) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder rewritten = null;
        for (int i = 0; i < text.length(); i++) {
            char original = text.charAt(i);
            char next = original;
            if (original != ' ' && original != '\n') {
                next = replacement(original, tick, baseIndex + i, widthOfCodePoint);
            }
            if (rewritten == null) {
                if (next == original) continue;
                rewritten = new StringBuilder(text.length());
                rewritten.append(text, 0, i);
            }
            rewritten.append(next);
        }
        return rewritten == null ? text : rewritten.toString();
    }

    static char replacement(char original, long tick, int index, IntUnaryOperator widthOfCodePoint) {
        int want = widthOfCodePoint.applyAsInt(original);
        int start = mix(tick, index);
        int poolSize = POOL.length();
        for (int n = 0; n < poolSize; n++) {
            char candidate = POOL.charAt(Math.floorMod(start + n, poolSize));
            if (candidate != original && widthOfCodePoint.applyAsInt(candidate) == want) {
                return candidate;
            }
        }
        return original;
    }

    private static int mix(long tick, int index) {
        return (int) (tick * 31 + index * 17);
    }
}
