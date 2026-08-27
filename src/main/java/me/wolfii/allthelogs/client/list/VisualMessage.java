package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;

/**
 * Turns a stored chat line into the string drawn in the list: trim each line, and turn a literal
 * {@code \n} into a visual linebreak (still drawing those two characters) unless the line was
 * captured from a live session.
 */
public final class VisualMessage {
    private VisualMessage() {
    }

    public static boolean interpretEscapes(ChatLog log) {
        return log != null && !(log.source() instanceof LogSource.Session);
    }

    public static String visual(String message, boolean interpretEscapes) {
        if (message == null || message.isEmpty()) return "";
        String[] paragraphs = message.split("\n", -1);
        StringBuilder out = new StringBuilder(message.length());
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) out.append('\n');
            appendParagraph(out, paragraphs[i], interpretEscapes);
        }
        return trimNewlines(out.toString());
    }

    /**
     * Whether {@code index} in {@code visual} is the {@code \} or {@code n} of an interpreted linebreak token.
     */
    public static boolean escapeChar(String visual, int index, boolean interpretEscapes) {
        if (!interpretEscapes || visual == null || index < 0 || index >= visual.length()) return false;
        if (visual.charAt(index) == '\\') {
            return escapeStartsAt(visual, index);
        }
        if (visual.charAt(index) == 'n') {
            return escapeStartsAt(visual, index - 1);
        }
        return false;
    }

    private static boolean escapeStartsAt(String visual, int index) {
        if (index < 0 || index + 1 >= visual.length()) return false;
        if (visual.charAt(index) != '\\' || visual.charAt(index + 1) != 'n') return false;
        return index + 2 >= visual.length() || visual.charAt(index + 2) == '\n';
    }

    private static void appendParagraph(StringBuilder out, String paragraph, boolean interpretEscapes) {
        if (!interpretEscapes) {
            out.append(paragraph.trim());
            return;
        }
        int from = 0;
        boolean first = true;
        while (from <= paragraph.length()) {
            int escape = paragraph.indexOf("\\n", from);
            String chunk = escape < 0 ? paragraph.substring(from) : paragraph.substring(from, escape);
            if (!first) {
                out.append("\\n\n");
            }
            out.append(chunk.trim());
            first = false;
            if (escape < 0) break;
            from = escape + 2;
        }
    }

    /**
     * Strips leading and trailing newline characters from a display string. Literal {@code \n} tokens are kept.
     */
    public static String trimNewlines(String visual) {
        if (visual == null || visual.isEmpty()) return visual == null ? "" : visual;
        int start = 0;
        int end = visual.length();
        while (start < end && visual.charAt(start) == '\n') start++;
        while (end > start && visual.charAt(end - 1) == '\n') end--;
        return start == 0 && end == visual.length() ? visual : visual.substring(start, end);
    }
}
