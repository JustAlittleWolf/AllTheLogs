package me.wolfii.allthelogs.data.parse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts every match of a pattern out of a stored message and moves the formatting runs with the characters
 * that remain. Empty matches remove nothing.
 */
public final class TextRemoval {
    private TextRemoval() {
    }

    /**
     * Non-empty matches of {@code pattern} in {@code text} as {@code [start, end)} pairs, in order.
     */
    public static List<int[]> ranges(String text, Pattern pattern) {
        List<int[]> ranges = new ArrayList<>();
        if (text == null || text.isEmpty() || pattern == null) return ranges;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (matcher.end() > matcher.start()) ranges.add(new int[]{matcher.start(), matcher.end()});
        }
        return ranges;
    }

    /**
     * {@code parsed} without the matches of {@code pattern}, or {@code parsed} itself when nothing matches.
     */
    public static FormattingCodes.Parsed remove(FormattingCodes.Parsed parsed, Pattern pattern) {
        String text = parsed.text();
        List<int[]> ranges = ranges(text, pattern);
        if (ranges.isEmpty()) return parsed;
        int[] formats = PackedFormatting.perChar(parsed.formatting(), text.length());
        StringBuilder kept = new StringBuilder(text.length());
        int[] keptFormats = new int[text.length()];
        int out = 0;
        int cursor = 0;
        for (int[] range : ranges) {
            for (int i = cursor; i < range[0]; i++) {
                kept.append(text.charAt(i));
                keptFormats[out++] = formats[i];
            }
            cursor = range[1];
        }
        for (int i = cursor; i < text.length(); i++) {
            kept.append(text.charAt(i));
            keptFormats[out++] = formats[i];
        }
        return new FormattingCodes.Parsed(kept.toString(), PackedFormatting.pack(Arrays.copyOf(keptFormats, out)));
    }
}
