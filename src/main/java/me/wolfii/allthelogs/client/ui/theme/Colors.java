package me.wolfii.allthelogs.client.ui.theme;

/**
 * Palette for the log browser: match vs context text, hover cards, and store-info tooltips.
 */
public final class Colors {
    public static final int MATCH_TEXT = 0xFFFFFFFF;
    /**
     * Slightly darker than white-green so highlighted substrings stay readable on a dim list.
     */
    public static final int MATCH_HIGHLIGHT = 0xFFA8DC9C;
    public static final int TIMESTAMP = 0xFFA0A0A0;
    public static final int CONTEXT_TEXT = 0xFFA4A4A4;
    public static final int INFO_DATE = 0xFFD8D8D8;
    public static final int INFO_VERSION = 0xFF7EB8D4;
    public static final int INFO_FILE = 0xFFB8A9E0;
    /**
     * Literal {@code \n} tokens that were turned into visual linebreaks.
     */
    public static final int ESCAPE_TEXT = 0xFF6E6E6E;
    public static final int META_LABEL = 0xFF9A9A9A;
    public static final int META_VALUE = 0xFFD8D8D8;
    public static final int META_NUMBER = 0xFF7EB8D4;
    public static final int META_SIZE = 0xFFA8C48A;

    private Colors() {
    }
}
