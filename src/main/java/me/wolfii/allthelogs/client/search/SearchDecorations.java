package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Visual {@code /pattern/} chrome for the search box. The slashes and the {@code i} flag are not part of
 * the field value: the box holds only the pattern, and the chrome is drawn beside it.
 */
public final class SearchDecorations {
    /** Space between a decoration and the editable pattern, in pixels. */
    public static final int GAP = 1;

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

    /**
     * Colour of one decoration character. The slashes use the group colour; the {@code i} flag uses the
     * anchor colour, matching the old in-text wrapping.
     */
    public static int decorationColor(String decoration, int index) {
        if (decoration == null || index < 0 || index >= decoration.length()) return Colors.REGEX_GROUP;
        if (index == 0) return Colors.REGEX_GROUP;
        return Colors.REGEX_ANCHOR;
    }

    /**
     * Colours the editable pattern. {@code visible} is the slice currently on screen, starting at
     * {@code start} in the full value.
     */
    public static FormattedCharSequence format(SearchFilter filter, String visible, int start) {
        if (visible == null || visible.isEmpty()) return FormattedCharSequence.EMPTY;
        if (filter != null && filter.regex() && filter.hasText() && !filter.invalidRegex()) {
            String pattern = filter.text();
            int from = Math.max(0, Math.min(start, pattern.length()));
            int to = Math.min(pattern.length(), from + visible.length());
            if (from < to) {
                return RegexHighlight.highlight(pattern.substring(from, to)).getVisualOrderText();
            }
        }
        int color = filter != null && filter.invalidRegex() ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT;
        return FormattedCharSequence.forward(visible, Style.EMPTY.withColor(color & 0xFFFFFF));
    }
}
