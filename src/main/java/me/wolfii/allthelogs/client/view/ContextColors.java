package me.wolfii.allthelogs.client.view;

/**
 * Colours for the message list. Hits stay white, with a slightly darker green on the matched
 * substring. Context lines use one medium grey.
 */
public final class ContextColors {
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
    public static final int META_LABEL = 0xFF9A9A9A;
    public static final int META_VALUE = 0xFFD8D8D8;
    public static final int META_NUMBER = 0xFF7EB8D4;
    public static final int META_SIZE = 0xFFA8C48A;

    private ContextColors() {
    }
}
