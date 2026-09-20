package me.wolfii.allthelogs.client.search;

import java.util.regex.Pattern;

/**
 * DuckDB RE2 letter flags the filter UI accepts. {@code i} is kept in lockstep with
 * {@link SearchFilter#caseSensitive()}: ignore-case means {@code i} is present.
 * <p>
 * Only letters that RE2 and Java treat the same way are allowed, so search-bar highlighting
 * can compile the same pattern DuckDB runs. {@code U} is omitted: RE2 uses it for ungreedy
 * matching, Java for Unicode character classes.
 */
public final class RegexFlags {
    /**
     * Inline RE2 flags with matching Java {@link Pattern} meaning: ignore-case, multiline, dotall.
     */
    public static final String VALID = "ims";

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

    /**
     * RE2 inline group {@code (?ims)} in a stable letter order.
     */
    public static String re2Inline(String flags) {
        String cleaned = sanitize(flags);
        StringBuilder inline = new StringBuilder(VALID.length());
        appendIfPresent(inline, cleaned, 'i');
        appendIfPresent(inline, cleaned, 'm');
        appendIfPresent(inline, cleaned, 's');
        return inline.toString();
    }

    /**
     * Extra Java compile bits so {@code (?i)} folds Unicode the way RE2 does. {@code m} and {@code s}
     * come from the inline group on the DuckDB pattern string, not from these bits.
     */
    public static int highlightBits(String flags) {
        return ignoreCase(flags) ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
    }

    private static void appendIfPresent(StringBuilder into, String flags, char letter) {
        if (flags.indexOf(letter) >= 0) into.append(letter);
    }
}
