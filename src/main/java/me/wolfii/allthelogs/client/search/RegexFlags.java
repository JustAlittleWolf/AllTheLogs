package me.wolfii.allthelogs.client.search;

import java.util.regex.Pattern;

/**
 * Java / RE2 letter flags the filter UI accepts. {@code i} is kept in lockstep with
 * {@link SearchFilter#caseSensitive()}: ignore-case means {@code i} is present.
 */
public final class RegexFlags {
    /**
     * Letters {@link Pattern} understands as inline / compile flags, plus RE2's {@code U}.
     */
    public static final String VALID = "dimsuxU";

    private RegexFlags() {
    }

    /**
     * Whether {@code flags} contains only unique valid letters, in the order they were typed.
     */
    public static boolean isLegal(String flags) {
        return flags != null && flags.equals(sanitize(flags));
    }

    /**
     * Drops invalid letters and later duplicates, keeping first-seen order.
     */
    public static String sanitize(String flags) {
        if (flags == null || flags.isEmpty()) return "";
        StringBuilder cleaned = new StringBuilder(flags.length());
        for (int i = 0; i < flags.length(); i++) {
            char c = flags.charAt(i);
            if (!isValidLetter(c)) continue;
            if (cleaned.indexOf(String.valueOf(c)) >= 0) continue;
            cleaned.append(c);
        }
        return cleaned.toString();
    }

    public static boolean isValidLetter(char c) {
        return VALID.indexOf(c) >= 0;
    }

    public static boolean ignoreCase(String flags) {
        return flags != null && flags.indexOf('i') >= 0;
    }

    /**
     * Adds or removes {@code i} without reordering the other letters.
     */
    public static String withIgnoreCase(String flags, boolean ignore) {
        String cleaned = sanitize(flags);
        boolean hasI = ignoreCase(cleaned);
        if (ignore == hasI) return cleaned;
        if (ignore) return cleaned + "i";
        return cleaned.replace("i", "");
    }

    public static int toPatternFlags(String flags) {
        String cleaned = sanitize(flags);
        int bits = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            bits |= bit(cleaned.charAt(i));
        }
        if ((bits & Pattern.CASE_INSENSITIVE) != 0) {
            bits |= Pattern.UNICODE_CASE;
        }
        return bits;
    }

    /**
     * RE2 inline group {@code (?imsU)}, omitting letters DuckDB does not accept.
     */
    public static String re2Inline(String flags) {
        String cleaned = sanitize(flags);
        StringBuilder inline = new StringBuilder(4);
        appendIfPresent(inline, cleaned, 'i');
        appendIfPresent(inline, cleaned, 'm');
        appendIfPresent(inline, cleaned, 's');
        appendIfPresent(inline, cleaned, 'U');
        return inline.toString();
    }

    private static void appendIfPresent(StringBuilder into, String flags, char letter) {
        if (flags.indexOf(letter) >= 0) into.append(letter);
    }

    private static int bit(char flag) {
        return switch (flag) {
            case 'd' -> Pattern.UNIX_LINES;
            case 'i' -> Pattern.CASE_INSENSITIVE;
            case 'm' -> Pattern.MULTILINE;
            case 's' -> Pattern.DOTALL;
            case 'u' -> Pattern.UNICODE_CASE;
            case 'x' -> Pattern.COMMENTS;
            case 'U' -> Pattern.UNICODE_CHARACTER_CLASS;
            default -> 0;
        };
    }
}
