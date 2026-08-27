package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.client.view.ContextColors;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.HighlightSpan;
import me.wolfii.allthelogs.client.view.MessageDisplay;
import me.wolfii.allthelogs.client.view.MessageWrap;
import me.wolfii.allthelogs.data.ChatLog;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Builds chat-line {@link Component}s with hex colours: white for hits, light green on the match, one grey for context.
 */
public final class MessageComponents {
    /**
     * Timestamp column sample used to reserve width for {@code HH:mm:ss} plus a gap before the message.
     */
    public static final String TIMESTAMP_GUTTER = "00:00:00  ";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MessageComponents() {
    }

    public static String matchCountText(int matches) {
        return matches > 99 ? ">99" : Integer.toString(matches);
    }

    /**
     * Text for the list's status chip: a persistent overlay, then loading, then the match count and search time.
     */
    public static Component listStatus(Component overlay, boolean loading, boolean showMatches, int matchCount,
                                       long elapsedMs) {
        if (overlay != null && !overlay.getString().isEmpty()) return overlay;
        if (loading) return Component.translatable("allthelogs.status.loading");
        if (!showMatches) return Component.empty();
        return Component.translatable("allthelogs.status.matches", matchCountText(matchCount),
            Long.toString(Math.max(0, elapsedMs)));
    }

    public static Component timestamp(DisplayRow row) {
        return colored(row.entry().timestamp().toLocalTime().withNano(0).format(TIME), ContextColors.TIMESTAMP);
    }

    /**
     * Compact hover card for a message timestamp: full date, version/user, wrapped source path.
     */
    public static List<Component> messageInfo(DisplayRow row) {
        return messageInfo(row, Integer.MAX_VALUE, text -> text.length());
    }

    public static List<Component> messageInfo(DisplayRow row, int maxWidth, ToIntFunction<String> widthOf) {
        List<Component> lines = new ArrayList<>();
        String date = row.entry().timestamp().withNano(0).format(FULL_DATE);
        lines.add(colored(date, ContextColors.INFO_DATE));
        String played = playedLine(row.chatLog());
        if (played != null) {
            lines.add(colored(played, ContextColors.INFO_VERSION));
        }
        String path = row.chatLog().source().fullPath();
        if (path != null && !path.isBlank()) {
            int width = Math.max(16, maxWidth);
            for (MessageWrap.Line line : MessageWrap.wrap(path, width, widthOf)) {
                lines.add(colored(line.text(), ContextColors.INFO_FILE));
            }
        }
        return lines;
    }

    static String playedLine(ChatLog log) {
        boolean version = log.minecraftVersion() != null
            && !log.minecraftVersion().isBlank()
            && !ChatLog.UNKNOWN_VERSION.equals(log.minecraftVersion());
        String user = log.minecraftUser();
        boolean named = user != null && !user.isBlank();
        if (version && named) {
            return "Version " + log.minecraftVersion() + " as " + user;
        }
        if (version) {
            return "Version " + log.minecraftVersion();
        }
        if (named) {
            return "Played as " + user;
        }
        return null;
    }

    public static Component dateHeader(LocalDate date) {
        return Component.literal(date.format(DATE));
    }

    public static Component message(DisplayRow row) {
        return messageRange(row, 0, row.message().length());
    }

    public static Component messageRange(DisplayRow row, int from, int to) {
        String full = row.message();
        int start = Math.clamp(from, 0, full.length());
        int end = Math.clamp(to, start, full.length());
        String text = full.substring(start, end);
        boolean interpret = MessageDisplay.interpretEscapes(row.chatLog());
        if (!row.match()) {
            return coloredRange(full, start, text, ContextColors.CONTEXT_TEXT, interpret);
        }
        if (row.highlights().isEmpty()) {
            return coloredRange(full, start, text, ContextColors.MATCH_TEXT, interpret);
        }
        MutableComponent result = Component.empty();
        int cursor = 0;
        for (HighlightSpan span : row.highlights()) {
            int highlightStart = Math.clamp(span.start() - start, 0, text.length());
            int highlightEnd = Math.clamp(span.end() - start, 0, text.length());
            if (highlightEnd <= highlightStart) continue;
            if (highlightStart > cursor) {
                result.append(coloredRange(full, start + cursor, text.substring(cursor, highlightStart),
                    ContextColors.MATCH_TEXT, interpret));
            }
            result.append(coloredRange(full, start + highlightStart, text.substring(highlightStart, highlightEnd),
                ContextColors.MATCH_HIGHLIGHT, interpret));
            cursor = highlightEnd;
        }
        if (cursor < text.length()) {
            result.append(coloredRange(full, start + cursor, text.substring(cursor), ContextColors.MATCH_TEXT,
                interpret));
        }
        return result;
    }

    private static Component coloredRange(String full, int start, String text, int color, boolean interpret) {
        if (!interpret || text.isEmpty()) {
            return colored(text, color);
        }
        MutableComponent result = Component.empty();
        int i = 0;
        while (i < text.length()) {
            if (MessageDisplay.escapeChar(full, start + i, true)) {
                int run = i + 1;
                while (run < text.length() && MessageDisplay.escapeChar(full, start + run, true)) {
                    run++;
                }
                result.append(colored(text.substring(i, run), ContextColors.ESCAPE_TEXT));
                i = run;
            } else {
                int run = i + 1;
                while (run < text.length() && !MessageDisplay.escapeChar(full, start + run, true)) {
                    run++;
                }
                result.append(colored(text.substring(i, run), color));
                i = run;
            }
        }
        return result.getSiblings().size() == 1 ? result.getSiblings().getFirst() : result;
    }

    private static Component colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }
}
