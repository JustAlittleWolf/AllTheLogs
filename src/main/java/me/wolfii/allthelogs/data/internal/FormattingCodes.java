package me.wolfii.allthelogs.data.internal;

/// Removes Minecraft formatting codes from chat text as it is ingested.
///
/// A code is the section sign followed by one of `[0-9a-fklmnor]`, and both characters are dropped. Section signs that
/// are not followed by a code character are kept, as are all other characters.
public final class FormattingCodes {
    private static final char SECTION = '\u00a7';

    private FormattingCodes() {
    }

    /// @return `message` without formatting codes, or `message` itself when it contains none
    public static String strip(String message) {
        if (message.indexOf(SECTION) < 0) return message;

        StringBuilder stripped = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if (current == SECTION && i + 1 < message.length() && isCode(message.charAt(i + 1))) {
                i++;
                continue;
            }
            stripped.append(current);
        }
        return stripped.toString();
    }

    private static boolean isCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
            || c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r';
    }
}
