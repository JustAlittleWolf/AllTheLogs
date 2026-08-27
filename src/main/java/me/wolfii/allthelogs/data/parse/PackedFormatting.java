package me.wolfii.allthelogs.data.parse;

import java.util.Arrays;

/**
 * Packed {@code int[]} of {@code (offset, charCount, format)} triples describing non-overlapping runs.
 * Offsets are into the stored (stripped) message. {@code null} means the line has no formatting.
 * <p>
 * One format int per run, so each character has at most one colour and one set of style flags.
 * Reset is not stored: unformatted characters are simply omitted. Format bits:
 * <ul>
 *   <li>0–23: RGB</li>
 *   <li>24: colour present</li>
 *   <li>25: bold</li>
 *   <li>26: italic</li>
 *   <li>27: underline</li>
 *   <li>28: strikethrough</li>
 *   <li>29: obfuscated</li>
 * </ul>
 */
public final class PackedFormatting {
    public static final int HAS_COLOR = 1 << 24;
    public static final int BOLD = 1 << 25;
    public static final int ITALIC = 1 << 26;
    public static final int UNDERLINE = 1 << 27;
    public static final int STRIKETHROUGH = 1 << 28;
    public static final int OBFUSCATED = 1 << 29;
    private static final int STYLE_MASK = BOLD | ITALIC | UNDERLINE | STRIKETHROUGH | OBFUSCATED;

    private PackedFormatting() {
    }

    public static int color(int rgb) {
        return (rgb & 0xFFFFFF) | HAS_COLOR;
    }

    public static int withStyle(int format, int flags) {
        return format | (flags & STYLE_MASK);
    }

    public static boolean isEmpty(int format) {
        return format == 0;
    }

    public static boolean hasColor(int format) {
        return (format & HAS_COLOR) != 0;
    }

    public static int rgb(int format) {
        return format & 0xFFFFFF;
    }

    public static boolean bold(int format) {
        return (format & BOLD) != 0;
    }

    public static boolean italic(int format) {
        return (format & ITALIC) != 0;
    }

    public static boolean underline(int format) {
        return (format & UNDERLINE) != 0;
    }

    public static boolean strikethrough(int format) {
        return (format & STRIKETHROUGH) != 0;
    }

    public static boolean obfuscated(int format) {
        return (format & OBFUSCATED) != 0;
    }

    /**
     * Format at {@code index}, or {@code 0} when unformatted / out of range / {@code packed} is null.
     */
    public static int at(int[] packed, int index) {
        if (packed == null || index < 0) return 0;
        for (int i = 0; i + 2 < packed.length; i += 3) {
            int start = packed[i];
            int end = start + packed[i + 1];
            if (index >= start && index < end) return packed[i + 2];
        }
        return 0;
    }

    /**
     * Merges adjacent equal non-zero formats. {@code null} or all zeros become {@code null}.
     */
    public static int[] pack(int[] perChar) {
        if (perChar == null || perChar.length == 0) return null;
        int[] packed = new int[perChar.length * 3];
        int size = 0;
        int runStart = -1;
        int runFormat = 0;
        for (int i = 0; i <= perChar.length; i++) {
            int format = i < perChar.length ? perChar[i] : 0;
            if (runStart >= 0 && format != runFormat) {
                packed[size] = runStart;
                packed[size + 1] = i - runStart;
                packed[size + 2] = runFormat;
                size += 3;
                runStart = -1;
            }
            if (runStart < 0 && format != 0) {
                runStart = i;
                runFormat = format;
            }
        }
        if (size == 0) return null;
        return Arrays.copyOf(packed, size);
    }

    public static int[] perChar(int[] packed, int length) {
        int[] perChar = new int[Math.max(0, length)];
        if (packed == null) return perChar;
        for (int i = 0; i + 2 < packed.length; i += 3) {
            int start = Math.clamp(packed[i], 0, perChar.length);
            int end = Math.clamp(packed[i] + packed[i + 1], start, perChar.length);
            Arrays.fill(perChar, start, end, packed[i + 2]);
        }
        return perChar;
    }

    /**
     * DuckDB {@code INTEGER[]} / JSON list literal, or {@code null}.
     */
    public static String toSqlLiteral(int[] packed) {
        if (packed == null || packed.length == 0) return null;
        StringBuilder text = new StringBuilder(2 + packed.length * 4);
        text.append('[');
        for (int i = 0; i < packed.length; i++) {
            if (i > 0) text.append(',');
            text.append(packed[i]);
        }
        return text.append(']').toString();
    }

    /**
     * Parses {@code [1, 2, 3]} / {@code [1,2,3]} as produced by DuckDB. Empty or {@code null} yield {@code null}.
     */
    public static int[] fromSqlLiteral(String literal) {
        if (literal == null || literal.isBlank() || "null".equalsIgnoreCase(literal) || "[]".equals(literal)) {
            return null;
        }
        String body = literal;
        if (body.charAt(0) == '[') {
            int end = body.endsWith("]") ? body.length() - 1 : body.length();
            body = body.substring(1, end);
        }
        if (body.isBlank()) return null;
        String[] parts = body.split(",");
        int[] values = new int[parts.length];
        int size = 0;
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            values[size++] = Integer.parseInt(token);
        }
        if (size == 0) return null;
        return size == values.length ? values : Arrays.copyOf(values, size);
    }
}
