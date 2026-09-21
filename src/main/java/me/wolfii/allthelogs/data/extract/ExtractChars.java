package me.wolfii.allthelogs.data.extract;

/**
 * ASCII character classes matching Java regex {@code \\s} / {@code \\d} / {@code \\S}
 * without {@code UNICODE_CHARACTER_CLASS}.
 */
final class ExtractChars {
    private ExtractChars() {
    }

    static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r' || c == 0x0B;
    }

    static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean isHexOrDash(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '-';
    }

    /** Index after a {@code \\S+} token starting at {@code start}, or {@code start} if empty. */
    static int tokenEnd(String line, int start) {
        int i = start;
        int length = line.length();
        while (i < length && !isWhitespace(line.charAt(i))) i++;
        return i;
    }

    /** Same as {@code \\s*$} from {@code start}. */
    static boolean onlyWhitespaceFrom(String line, int start) {
        for (int i = start; i < line.length(); i++) {
            if (!isWhitespace(line.charAt(i))) return false;
        }
        return true;
    }
}
