package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.PackedFormatting;

import java.util.Arrays;

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
        return layout(message, interpretEscapes).text();
    }

    /**
     * Display text and remapped formatting for a stored entry, laid out once.
     */
    public static Prepared prepare(ChatEntry entry) {
        if (entry == null) return Prepared.EMPTY;
        boolean interpret = interpretEscapes(entry.chatLog());
        Layout layout = layout(entry.message(), interpret);
        return new Prepared(layout.text(), remap(layout, entry.message(), entry.formatting()));
    }

    /**
     * Packed formatting remapped from stored-message offsets onto {@link #visual(String, boolean)}.
     */
    public static long[] remapFormatting(String message, long[] formatting, boolean interpretEscapes) {
        return remap(layout(message, interpretEscapes), message, formatting);
    }

    public record Prepared(String text, long[] formatting) {
        static final Prepared EMPTY = new Prepared("", null);
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

    private record Layout(String text, int[] storedIndex) {
    }

    private static final class Buffer {
        private final StringBuilder text = new StringBuilder();
        private int[] storedIndex = new int[32];

        void append(char c, int stored) {
            int size = text.length();
            if (size == storedIndex.length) {
                storedIndex = Arrays.copyOf(storedIndex, storedIndex.length + (storedIndex.length >> 1) + 8);
            }
            text.append(c);
            storedIndex[size] = stored;
        }

        void appendTrimmed(String chunk, int chunkStart) {
            int begin = 0;
            int stop = chunk.length();
            while (begin < stop && chunk.charAt(begin) <= ' ') begin++;
            while (stop > begin && chunk.charAt(stop - 1) <= ' ') stop--;
            for (int i = begin; i < stop; i++) {
                append(chunk.charAt(i), chunkStart + i);
            }
        }

        Layout trimmedNewlines() {
            int start = 0;
            int end = text.length();
            while (start < end && text.charAt(start) == '\n') start++;
            while (end > start && text.charAt(end - 1) == '\n') end--;
            return new Layout(text.substring(start, end), Arrays.copyOfRange(storedIndex, start, end));
        }
    }

    private static long[] remap(Layout layout, String storedMessage, long[] formatting) {
        if (formatting == null || formatting.length == 0) return null;
        if (layout.text.isEmpty()) return null;
        int[] stored = PackedFormatting.perChar(formatting, storedMessage == null ? 0 : storedMessage.length());
        int[] visual = new int[layout.text.length()];
        for (int i = 0; i < visual.length; i++) {
            int storedIndex = layout.storedIndex[i];
            if (storedIndex >= 0 && storedIndex < stored.length) {
                visual[i] = stored[storedIndex];
            }
        }
        return PackedFormatting.pack(visual);
    }

    private static Layout layout(String message, boolean interpretEscapes) {
        if (message == null || message.isEmpty()) return new Layout("", new int[0]);
        Buffer buffer = new Buffer();
        String[] paragraphs = message.split("\n", -1);
        int paragraphStart = 0;
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) {
                buffer.append('\n', paragraphStart - 1);
            }
            appendParagraph(buffer, paragraphs[i], paragraphStart, interpretEscapes);
            paragraphStart += paragraphs[i].length() + 1;
        }
        return buffer.trimmedNewlines();
    }

    private static void appendParagraph(Buffer buffer, String paragraph, int paragraphStart,
                                        boolean interpretEscapes) {
        if (!interpretEscapes) {
            buffer.appendTrimmed(paragraph, paragraphStart);
            return;
        }
        int from = 0;
        boolean first = true;
        while (from <= paragraph.length()) {
            int escape = paragraph.indexOf("\\n", from);
            String chunk = escape < 0 ? paragraph.substring(from) : paragraph.substring(from, escape);
            if (!first) {
                int tokenAt = paragraphStart + from - 2;
                buffer.append('\\', tokenAt);
                buffer.append('n', tokenAt + 1);
                buffer.append('\n', -1);
            }
            buffer.appendTrimmed(chunk, paragraphStart + from);
            first = false;
            if (escape < 0) break;
            from = escape + 2;
        }
    }
}
