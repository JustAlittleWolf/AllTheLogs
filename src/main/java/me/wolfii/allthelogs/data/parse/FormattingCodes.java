package me.wolfii.allthelogs.data.parse;

import java.util.Arrays;

/**
 * Minecraft legacy {@code §} formatting codes, as used in log files.
 * Codes are stripped from stored text; colour and style are kept as {@link PackedFormatting}.
 * Java Edition rules: a colour code clears styles; {@code §r} resets and is not stored.
 */
public final class FormattingCodes {
    private static final char SECTION = '\u00a7';
    /**
     * Java Edition named colours, matching
     * <a href="https://minecraft.wiki/w/Formatting_codes">Formatting codes</a>.
     */
    private static final int[] COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private FormattingCodes() {
    }

    /**
     * @return {@code message} without formatting codes, or {@code message} itself when it contains none
     */
    public static String strip(String message) {
        return parse(message).text();
    }

    public static Parsed parse(String message) {
        return parse(message, 0);
    }

    /**
     * Strips codes from {@code message}. {@code initialFormat} is the flattened style at the start of this
     * run (live chat), or {@code 0} for log files.
     */
    public static Parsed parse(String message, int initialFormat) {
        if (message == null) return Parsed.plain("");
        if (message.indexOf(SECTION) < 0) {
            if (PackedFormatting.isEmpty(initialFormat) || message.isEmpty()) return Parsed.plain(message);
            int[] perChar = new int[message.length()];
            Arrays.fill(perChar, initialFormat);
            return new Parsed(message, PackedFormatting.pack(perChar));
        }

        StringBuilder stripped = new StringBuilder(message.length());
        int[] perChar = new int[message.length()];
        int current = initialFormat;
        for (int i = 0; i < message.length(); i++) {
            char next = message.charAt(i);
            if (next == SECTION && i + 1 < message.length()) {
                int applied = applyCode(message.charAt(i + 1), current);
                if (applied >= 0) {
                    current = applied;
                    i++;
                    continue;
                }
            }
            perChar[stripped.length()] = current;
            stripped.append(next);
        }
        String text = stripped.toString();
        if (text.length() == message.length() && PackedFormatting.isEmpty(initialFormat)) {
            return Parsed.plain(message);
        }
        long[] packed = PackedFormatting.pack(Arrays.copyOf(perChar, text.length()));
        return new Parsed(text, packed);
    }

    /**
     * @return the new format, or {@code -1} when {@code code} is not a formatting character
     */
    private static int applyCode(char code, int current) {
        char lower = code <= 'Z' && code >= 'A' ? (char) (code + 32) : code;
        if (lower >= '0' && lower <= '9') {
            return PackedFormatting.color(COLORS[lower - '0']);
        }
        if (lower >= 'a' && lower <= 'f') {
            return PackedFormatting.color(COLORS[10 + lower - 'a']);
        }
        return switch (lower) {
            case 'k' -> current | PackedFormatting.OBFUSCATED;
            case 'l' -> current | PackedFormatting.BOLD;
            case 'm' -> current | PackedFormatting.STRIKETHROUGH;
            case 'n' -> current | PackedFormatting.UNDERLINE;
            case 'o' -> current | PackedFormatting.ITALIC;
            case 'r' -> 0;
            default -> -1;
        };
    }

    /**
     * Stripped text plus packed formatting. {@code formatting} is {@code null} when nothing is styled.
     * {@code text} is {@code message} itself when it contains no codes.
     */
    public record Parsed(String text, long[] formatting) {
        public static Parsed plain(String text) {
            return new Parsed(text, null);
        }
    }
}
