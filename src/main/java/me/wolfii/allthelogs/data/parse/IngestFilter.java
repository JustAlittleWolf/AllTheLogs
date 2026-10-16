package me.wolfii.allthelogs.data.parse;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Patterns for chat lines that are never stored, set from the config. Both file import and live capture
 * check the stored text, after {@code §} codes and placeholder characters are removed, so a pattern
 * behaves like a regex search in the message browser. Lines already in the store are not touched.
 */
public final class IngestFilter {
    private static volatile List<Pattern> ignored = List.of();

    private IngestFilter() {
    }

    public static void setIgnored(List<Pattern> patterns) {
        ignored = patterns == null ? List.of() : List.copyOf(patterns);
    }

    public static boolean ignores(String text) {
        List<Pattern> patterns = ignored;
        if (patterns.isEmpty() || text == null) return false;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) return true;
        }
        return false;
    }
}
