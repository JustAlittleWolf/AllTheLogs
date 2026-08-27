package me.wolfii.allthelogs.data.parse;

import java.util.Arrays;

/**
 * Packed formatting runs. Each run is one {@code long}:
 * <ul>
 *   <li>bits 0–15: start offset in the stripped message (0–65535)</li>
 *   <li>bits 16–31: character count (0–65535)</li>
 *   <li>bits 32–63: format</li>
 * </ul>
 * Minecraft chat cannot exceed 65535 characters, so one {@code long} holds a whole run.
 * {@code null} means the line has no formatting. Reset is not stored.
 * <p>
 * Format bits:
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
    private static final int RANGE_MASK = 0xFFFF;

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

    public static long run(int offset, int count, int format) {
        return (offset & RANGE_MASK)
            | ((long) (count & RANGE_MASK) << 16)
            | ((format & 0xFFFFFFFFL) << 32);
    }

    public static int offset(long run) {
        return (int) (run & RANGE_MASK);
    }

    public static int count(long run) {
        return (int) ((run >>> 16) & RANGE_MASK);
    }

    public static int format(long run) {
        return (int) (run >>> 32);
    }

    /**
     * Format at {@code index}, or {@code 0} when unformatted / out of range / {@code packed} is null.
     */
    public static int at(long[] packed, int index) {
        if (packed == null || index < 0) return 0;
        for (long run : packed) {
            int start = offset(run);
            if (index >= start && index < start + count(run)) return format(run);
        }
        return 0;
    }

    /**
     * Merges adjacent equal non-zero formats. {@code null} or all zeros become {@code null}.
     */
    public static long[] pack(int[] perChar) {
        if (perChar == null || perChar.length == 0) return null;
        long[] packed = new long[perChar.length];
        int size = 0;
        int runStart = -1;
        int runFormat = 0;
        for (int i = 0; i <= perChar.length; i++) {
            int next = i < perChar.length ? perChar[i] : 0;
            if (runStart >= 0 && next != runFormat) {
                packed[size++] = run(runStart, i - runStart, runFormat);
                runStart = -1;
            }
            if (runStart < 0 && next != 0) {
                runStart = i;
                runFormat = next;
            }
        }
        if (size == 0) return null;
        return Arrays.copyOf(packed, size);
    }

    public static int[] perChar(long[] packed, int length) {
        int[] perChar = new int[Math.max(0, length)];
        if (packed == null) return perChar;
        for (long run : packed) {
            int start = Math.clamp(offset(run), 0, perChar.length);
            int end = Math.clamp(start + count(run), start, perChar.length);
            Arrays.fill(perChar, start, end, format(run));
        }
        return perChar;
    }

    /**
     * DuckDB {@code BIGINT[]} literal, or {@code null}.
     */
    public static String toSqlLiteral(long[] packed) {
        if (packed == null || packed.length == 0) return null;
        StringBuilder text = new StringBuilder(2 + packed.length * 12);
        text.append('[');
        for (int i = 0; i < packed.length; i++) {
            if (i > 0) text.append(',');
            text.append(packed[i]);
        }
        return text.append(']').toString();
    }

    /**
     * Parses a {@code BIGINT[]} / JSON list literal. Empty or {@code null} yield {@code null}.
     */
    public static long[] fromSqlLiteral(String literal) {
        if (literal == null || literal.isBlank() || "null".equalsIgnoreCase(literal) || "[]".equals(literal)) {
            return null;
        }
        String body = literal.charAt(0) == '[' && literal.endsWith("]")
            ? literal.substring(1, literal.length() - 1)
            : literal;
        if (body.isBlank()) return null;
        String[] parts = body.split(",");
        long[] values = new long[parts.length];
        int size = 0;
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            values[size++] = Long.parseLong(token);
        }
        if (size == 0) return null;
        return size == values.length ? values : Arrays.copyOf(values, size);
    }
}
