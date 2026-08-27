package me.wolfii.allthelogs.client.list;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Wraps a chat line to a pixel width and maps pointer coordinates onto character indexes using prefix widths,
 * so selection matches what is drawn. Hard newlines in the stored message become their own visual rows.
 */
public final class MessageWrap {
    private MessageWrap() {
    }

    /**
     * Pixel width of {@code text[from, to)}. Indexes are into the original message.
     */
    @FunctionalInterface
    public interface RangeWidth {
        int width(int from, int to);
    }

    /**
     * One visual row of a wrapped message.
     *
     * @param text  the characters drawn on this row, never including a {@code \n}
     * @param start index of {@code text} in the original message
     */
    public record Line(String text, int start) {
    }

    public static List<String> lines(String text, int maxWidth, ToIntFunction<String> widthOf) {
        return wrap(text, maxWidth, widthOf).stream().map(Line::text).toList();
    }

    public static List<String> lines(String text, int maxWidth, RangeWidth widthOf) {
        return wrap(text, maxWidth, widthOf).stream().map(Line::text).toList();
    }

    public static List<Line> wrap(String text, int maxWidth, ToIntFunction<String> widthOf) {
        return wrap(text, maxWidth, substringWidth(text, widthOf));
    }

    public static List<Line> wrap(String text, int maxWidth, RangeWidth widthOf) {
        if (text == null || text.isEmpty()) return List.of(new Line("", 0));
        List<Line> lines = new ArrayList<>();
        int index = 0;
        while (true) {
            int newline = text.indexOf('\n', index);
            int end = newline < 0 ? text.length() : newline;
            wrapParagraph(text, index, end, maxWidth, widthOf, lines);
            if (newline < 0) break;
            index = newline + 1;
            if (index == text.length()) {
                lines.add(new Line("", index));
                break;
            }
        }
        return List.copyOf(lines);
    }

    public static int lineCount(String text, int maxWidth, ToIntFunction<String> widthOf) {
        return wrap(text, maxWidth, widthOf).size();
    }

    public static int lineCount(String text, int maxWidth, RangeWidth widthOf) {
        return wrap(text, maxWidth, widthOf).size();
    }

    /**
     * Character index whose left edge is at or just after {@code x} pixels into {@code text}.
     */
    public static int indexAtX(String text, int x, ToIntFunction<String> widthOf) {
        return indexAtX(text, x, substringWidth(text, widthOf));
    }

    public static int indexAtX(String text, int x, RangeWidth widthOf) {
        if (text == null || text.isEmpty() || x <= 0) return 0;
        int previousWidth = 0;
        for (int i = 1; i <= text.length(); i++) {
            int width = widthOf.width(0, i);
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
        return charIndex(text, maxWidth, line, x, substringWidth(text, widthOf));
    }

    /**
     * Range widths from one pass over per-character advances, so wrapping does not remeasure prefixes.
     */
    public static RangeWidth prefixWidths(int length, IntUnaryOperator charWidth) {
        int size = Math.max(0, length);
        int[] prefix = new int[size + 1];
        for (int i = 0; i < size; i++) {
            prefix[i + 1] = prefix[i] + charWidth.applyAsInt(i);
        }
        return (from, to) -> {
            int start = Math.clamp(from, 0, size);
            int end = Math.clamp(to, 0, size);
            return end > start ? prefix[end] - prefix[start] : 0;
        };
    }

    public static int charIndex(String text, int maxWidth, int line, int x, RangeWidth widthOf) {
        List<Line> wrapped = wrap(text, maxWidth, widthOf);
        if (wrapped.isEmpty()) return 0;
        int clampedLine = Math.clamp(line, 0, wrapped.size() - 1);
        Line visual = wrapped.get(clampedLine);
        return visual.start() + indexAtX(visual.text(), x,
            (from, to) -> widthOf.width(visual.start() + from, visual.start() + to));
    }

    private static RangeWidth substringWidth(String text, ToIntFunction<String> widthOf) {
        return (from, to) -> widthOf.applyAsInt(text.substring(from, to));
    }

    private static void wrapParagraph(String text, int start, int end, int maxWidth,
                                      RangeWidth widthOf, List<Line> lines) {
        if (start == end) {
            lines.add(new Line("", start));
            return;
        }
        if (maxWidth <= 0 || maxWidth == Integer.MAX_VALUE
            || widthOf.width(start, end) <= maxWidth) {
            lines.add(new Line(text.substring(start, end), start));
            return;
        }
        int from = start;
        while (from < end) {
            int to = from;
            int breakAt = -1;
            while (to < end) {
                int next = to + 1;
                if (widthOf.width(from, next) > maxWidth) {
                    break;
                }
                to = next;
                if (canBreakAfter(text.charAt(to - 1))) {
                    breakAt = to;
                }
            }
            if (to == from) {
                to = Math.min(end, from + 1);
            } else if (to < end && breakAt > from) {
                to = breakAt;
            }
            lines.add(new Line(text.substring(from, to), from));
            from = to;
        }
    }

    private static boolean canBreakAfter(char c) {
        return c == ' ' || c == '\t' || c == '-';
    }
}
