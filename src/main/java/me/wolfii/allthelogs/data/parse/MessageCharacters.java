package me.wolfii.allthelogs.data.parse;

import java.util.Arrays;

/**
 * Characters that do not belong in stored chat text.
 * <p>
 * U+FFFC OBJECT REPLACEMENT CHARACTER is always removed. Chat Heads uses it as the placeholder
 * glyph for a player face. Unicode private-use characters are removed when
 * {@link #dropPrivateUseCharacters()} is set, which the messages settings tab controls.
 * Formatting runs are rebuilt so colours stay on the characters that remain.
 */
public final class MessageCharacters {
    /** U+FFFC OBJECT REPLACEMENT CHARACTER. */
    public static final char OBJECT_REPLACEMENT = '\uFFFC';
    public static final int OBJECT_REPLACEMENT_CODE_POINT = 0xFFFC;

    private static volatile boolean dropPrivateUseCharacters = true;

    private MessageCharacters() {
    }

    /**
     * Whether private-use characters are removed from messages. Defaults to {@code true}.
     * U+FFFC is removed either way.
     */
    public static boolean dropPrivateUseCharacters() {
        return dropPrivateUseCharacters;
    }

    /**
     * Turns private-use filtering on or off for later parses, imports, and query results.
     */
    public static void setDropPrivateUseCharacters(boolean drop) {
        dropPrivateUseCharacters = drop;
    }

    /**
     * {@code true} when {@code codePoint} is in a Unicode private-use area.
     */
    public static boolean isPrivateUse(int codePoint) {
        return Character.getType(codePoint) == Character.PRIVATE_USE;
    }

    /**
     * Removes dropped characters from {@code parsed} using {@link #dropPrivateUseCharacters()}.
     * Returns {@code parsed} itself when nothing is removed.
     */
    public static FormattingCodes.Parsed filter(FormattingCodes.Parsed parsed) {
        return filter(parsed, dropPrivateUseCharacters);
    }

    /**
     * Removes U+FFFC, and private-use characters when {@code dropPrivateUse} is set.
     * Returns {@code parsed} itself when nothing is removed.
     */
    public static FormattingCodes.Parsed filter(FormattingCodes.Parsed parsed, boolean dropPrivateUse) {
        if (parsed == null) return FormattingCodes.Parsed.plain("");
        String text = parsed.text();
        if (text == null || text.isEmpty() || !containsDropped(text, dropPrivateUse)) return parsed;

        int[] formats = PackedFormatting.perChar(parsed.formatting(), text.length());
        StringBuilder kept = new StringBuilder(text.length());
        int[] keptFormats = new int[text.length()];
        int out = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            if (isDropped(codePoint, dropPrivateUse)) {
                i += width;
                continue;
            }
            kept.appendCodePoint(codePoint);
            for (int offset = 0; offset < width; offset++) {
                keptFormats[out++] = formats[i + offset];
            }
            i += width;
        }
        return new FormattingCodes.Parsed(kept.toString(), PackedFormatting.pack(Arrays.copyOf(keptFormats, out)));
    }

    static boolean isDropped(int codePoint, boolean dropPrivateUse) {
        if (codePoint == OBJECT_REPLACEMENT_CODE_POINT) return true;
        return dropPrivateUse && isPrivateUse(codePoint);
    }

    private static boolean containsDropped(String text, boolean dropPrivateUse) {
        for (int i = 0; i < text.length(); ) {
            char next = text.charAt(i);
            if (next == OBJECT_REPLACEMENT) return true;
            if (dropPrivateUse && next >= '\uE000' && next <= '\uF8FF') return true;
            if (dropPrivateUse && Character.isHighSurrogate(next)) {
                int codePoint = text.codePointAt(i);
                if (isPrivateUse(codePoint)) return true;
                i += Character.charCount(codePoint);
                continue;
            }
            i++;
        }
        return false;
    }
}
