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
    public static final int CONTEXT_TEXT = 0xFFA0A0A0;

    private ContextColors() {
    }
}
