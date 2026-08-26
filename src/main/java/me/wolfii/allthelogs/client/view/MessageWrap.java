package me.wolfii.allthelogs.client.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Wraps a chat line to a pixel width and maps pointer coordinates onto character indexes using prefix widths,
 * so selection matches what is drawn.
 */
public final class MessageWrap {
    private MessageWrap() {
    }

    public static List<String> lines(String text, int maxWidth, ToIntFunction<String> widthOf) {
        if (text == null || text.isEmpty()) return List.of("");
        if (maxWidth <= 0 || maxWidth == Integer.MAX_VALUE) return List.of(text);
        if (widthOf.applyAsInt(text) <= maxWidth) return List.of(text);
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int breakAt = -1;
            while (end < text.length()) {
                int next = end + 1;
                if (widthOf.applyAsInt(text.substring(start, next)) > maxWidth) {
                    break;
                }
                end = next;
                char c = text.charAt(end - 1);
                if (c == ' ' || c == '\t' || c == '-') {
                    breakAt = end;
                }
            }
            if (end == start) {
                end = Math.min(text.length(), start + 1);
            } else if (end < text.length() && breakAt > start) {
                end = breakAt;
            }
            lines.add(text.substring(start, end));
            start = end;
        }
        return List.copyOf(lines);
    }

    public static int lineCount(String text, int maxWidth, ToIntFunction<String> widthOf) {
        return lines(text, maxWidth, widthOf).size();
    }

    /**
     * Character index whose left edge is at or just after {@code x} pixels into {@code text}.
     */
    public static int indexAtX(String text, int x, ToIntFunction<String> widthOf) {
        if (text == null || text.isEmpty() || x <= 0) return 0;
        int previousWidth = 0;
        for (int i = 1; i <= text.length(); i++) {
            int width = widthOf.applyAsInt(text.substring(0, i));
            if (width >= x) {
                int midpoint = previousWidth + (width - previousWidth) / 2;
                return x < midpoint ? i - 1 : i;
            }
            previousWidth = width;
        }
        return text.length();
    }

    /**
     * Character index in {@code text} for a pointer on wrapped visual line {@code line} at pixel {@code x}.
     */
    public static int charIndex(String text, int maxWidth, int line, int x, ToIntFunction<String> widthOf) {
        List<String> wrapped = lines(text, maxWidth, widthOf);
        if (wrapped.isEmpty()) return 0;
        int clampedLine = Math.clamp(line, 0, wrapped.size() - 1);
        int index = 0;
        for (int i = 0; i < clampedLine; i++) {
            index += wrapped.get(i).length();
        }
        return index + indexAtX(wrapped.get(clampedLine), x, widthOf);
    }
}
