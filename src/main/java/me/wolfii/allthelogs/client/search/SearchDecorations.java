package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Uneditable {@code /pattern/} wrapping for the search box, plus an {@code i} flag when the search is
 * case insensitive.
 */
public final class SearchDecorations {
    private SearchDecorations() {
    }

    public static boolean wraps(SearchFilter filter) {
        return filter != null && filter.regex();
    }

    public static String prefix(SearchFilter filter) {
        return wraps(filter) ? "/" : "";
    }

    public static String suffix(SearchFilter filter) {
        if (!wraps(filter)) return "";
        return filter.caseSensitive() ? "/" : "/i";
    }

    public static String wrap(SearchFilter filter, String text) {
        String inner = text == null ? "" : text;
        return prefix(filter) + inner + suffix(filter);
    }

    public static String unwrap(SearchFilter filter, String shown) {
        String value = shown == null ? "" : shown;
        String prefix = prefix(filter);
        String suffix = suffix(filter);
        if (!prefix.isEmpty() && value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        if (!suffix.isEmpty() && value.endsWith(suffix)) {
            value = value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    public static int clampCursor(SearchFilter filter, String shown, int cursor) {
        String prefix = prefix(filter);
        String suffix = suffix(filter);
        int min = prefix.length();
        int max = Math.max(min, (shown == null ? 0 : shown.length()) - suffix.length());
        return Math.clamp(cursor, min, max);
    }

    public static FormattedCharSequence format(SearchFilter filter, String visible, int start) {
        if (visible == null || visible.isEmpty()) return FormattedCharSequence.EMPTY;
        if (!wraps(filter)) {
            int color = Colors.SEARCH_TEXT;
            return FormattedCharSequence.forward(visible, Style.EMPTY.withColor(color & 0xFFFFFF));
        }
        String inner = filter.text();
        int prefixLen = prefix(filter).length();
        int innerEnd = prefixLen + inner.length();
        MutableComponent result = Component.empty();
        int runStart = 0;
        int runColor = colorAt(start, prefixLen, innerEnd, filter);
        for (int i = 1; i <= visible.length(); i++) {
            int color = i < visible.length()
                ? colorAt(start + i, prefixLen, innerEnd, filter)
                : runColor ^ 1;
            if (color != runColor) {
                result.append(slice(filter, visible, start, runStart, i, prefixLen, innerEnd, runColor));
                runStart = i;
                runColor = color;
            }
        }
        return result.getVisualOrderText();
    }

    private static Component slice(SearchFilter filter, String visible, int start, int from, int to,
                                   int prefixLen, int innerEnd, int color) {
        String piece = visible.substring(from, to);
        int abs = start + from;
        if (abs >= prefixLen && abs < innerEnd && !filter.invalidRegex()) {
            int innerFrom = Math.max(0, abs - prefixLen);
            return RegexHighlight.highlight(filter.text().substring(innerFrom, innerFrom + piece.length()));
        }
        return Component.literal(piece).withStyle(Style.EMPTY.withColor(color & 0xFFFFFF));
    }

    private static int colorAt(int index, int prefixLen, int innerEnd, SearchFilter filter) {
        if (index < prefixLen) return Colors.REGEX_GROUP;
        if (index >= innerEnd) {
            return index == innerEnd ? Colors.REGEX_GROUP : Colors.REGEX_ANCHOR;
        }
        return filter.invalidRegex() ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT;
    }
}
