package me.wolfii.allthelogs.runtime;

/**
 * Inclusive-exclusive character range of a search hit inside a message.
 */
public record HighlightSpan(int start, int end) {
    public HighlightSpan {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid span [" + start + ", " + end + ")");
        }
    }

    public int length() {
        return end - start;
    }
}
