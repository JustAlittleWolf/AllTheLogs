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
    public static final int GAP = 3;

    /** Extra left margin of the closing {@code /} or {@code /i}, beyond {@link #GAP}. */
    public static final int SUFFIX_EXTRA_MARGIN = 2;

    /** Pixels between the pattern (or the placeholder) and the closing mark. */
    public static int suffixGap() {
        return GAP + SUFFIX_EXTRA_MARGIN;
    }

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
     * Colour of one decoration character. The slashes and the {@code i} flag use the pattern hues,
     * darkened so the chrome stays distinct from the typed query.
     */
    public static int decorationColor(String decoration, int index) {
        if (decoration == null || index < 0 || index >= decoration.length()) return Colors.REGEX_CHROME;
        if (index == 0) return Colors.REGEX_CHROME;
        return Colors.REGEX_CHROME_FLAG;
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
