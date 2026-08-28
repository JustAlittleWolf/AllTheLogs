package me.wolfii.allthelogs.data.query;

/**
 * Detects Java regex constructs that DuckDB's RE2 engine cannot run.
 * <p>
 * {@link java.util.regex.Pattern} accepts lookarounds, backreferences, and possessive quantifiers, so a pattern can
 * compile in the search bar and still be rejected when the store query runs. RE2 is a subset: non-capturing groups,
 * {@code (?i)}-style flags, and Python named groups {@code (?P<name>...)} are fine.
 */
public final class Re2Regex {
    private Re2Regex() {
    }

    /**
     * A short description of the first unsupported construct, or {@code null} if RE2 should be able to compile
     * {@code pattern}. Does not check ordinary syntax errors such as an unclosed group.
     */
    public static String unsupportedConstruct(String pattern) {
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                if (i + 1 >= pattern.length()) return null;
                char escaped = pattern.charAt(i + 1);
                if (escaped >= '1' && escaped <= '9') {
                    return "backreference \\" + escaped;
                }
                i += 2;
                continue;
            }
            if (c == '[') {
                i = skipCharacterClass(pattern, i);
                continue;
            }
            if (c == '(' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '?') {
                String feature = unsupportedGroup(pattern, i + 2);
                if (feature != null) return feature;
                i += 2;
                continue;
            }
            if (isQuantifier(c) && i + 1 < pattern.length() && pattern.charAt(i + 1) == '+') {
                return "possessive quantifier " + c + "+";
            }
            if (c == '{') {
                int end = skipCountedQuantifier(pattern, i);
                if (end > i) {
                    if (end < pattern.length() && pattern.charAt(end) == '+') {
                        return "possessive quantifier";
                    }
                    i = end;
                    continue;
                }
            }
            i++;
        }
        return null;
    }

    private static boolean isQuantifier(char c) {
        return c == '*' || c == '+' || c == '?';
    }

    /**
     * @param start index of the character after {@code (?}
     */
    private static String unsupportedGroup(String pattern, int start) {
        if (start >= pattern.length()) return null;
        if (pattern.startsWith("!", start)) return "negative lookahead";
        if (pattern.startsWith("=", start)) return "positive lookahead";
        if (pattern.startsWith("<=", start)) return "positive lookbehind";
        if (pattern.startsWith("<!", start)) return "negative lookbehind";
        if (pattern.startsWith(">", start)) return "atomic group";
        if (pattern.startsWith("#", start)) return "comment group";
        if (pattern.startsWith("P=", start)) return "named backreference";
        if (pattern.startsWith("<", start)) return "named capturing group";
        if (pattern.startsWith("P<", start) || pattern.startsWith(":", start)) return null;
        if (isInlineFlag(pattern, start)) return null;
        return "group modifier (?";
    }

    /**
     * {@code (?imsU)} / {@code (?-i)} / {@code (?i:...)} flag groups. Anything else after {@code (?} is unsupported.
     */
    private static boolean isInlineFlag(String pattern, int start) {
        int i = start;
        if (i < pattern.length() && pattern.charAt(i) == '-') i++;
        boolean sawFlag = false;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == 'i' || c == 'm' || c == 's' || c == 'U') {
                sawFlag = true;
                i++;
                continue;
            }
            if (c == '-' && sawFlag) {
                i++;
                continue;
            }
            return (c == ':' || c == ')') && sawFlag;
        }
        return false;
    }

    /**
     * Index of the character after a {@code {n}}, {@code {n,}}, or {@code {n,m}} quantifier, or {@code start}
     * if this {@code {} is not a counted quantifier.
     */
    private static int skipCountedQuantifier(String pattern, int start) {
        int i = start + 1;
        if (i >= pattern.length() || !Character.isDigit(pattern.charAt(i))) return start;
        while (i < pattern.length() && Character.isDigit(pattern.charAt(i))) i++;
        if (i < pattern.length() && pattern.charAt(i) == ',') {
            i++;
            while (i < pattern.length() && Character.isDigit(pattern.charAt(i))) i++;
        }
        if (i < pattern.length() && pattern.charAt(i) == '}') return i + 1;
        return start;
    }

    /**
     * Index of the character after the closing {@code ]} of the class that starts at {@code start}.
     */
    private static int skipCharacterClass(String pattern, int start) {
        int i = start + 1;
        if (i < pattern.length() && pattern.charAt(i) == '^') i++;
        if (i < pattern.length() && pattern.charAt(i) == ']') i++;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == ']') return i + 1;
            i++;
        }
        return pattern.length();
    }
}
