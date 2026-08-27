package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.client.view.ContextColors;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.HighlightSpan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    private MessageComponents() {
    }

    public static String matchCountText(int matches) {
        return matches > 99 ? ">99" : Integer.toString(matches);
    }

    /**
     * Text for the list's status chip: a persistent overlay, then loading, then the match count.
     */
    public static Component listStatus(Component overlay, boolean loading, boolean showMatches, int matchCount) {
        if (overlay != null && !overlay.getString().isEmpty()) return overlay;
        if (loading) return Component.translatable("allthelogs.status.loading");
        if (!showMatches) return Component.empty();
        return Component.translatable("allthelogs.status.matches", matchCountText(matchCount));
    }

    public static Component timestamp(DisplayRow row) {
        return colored(row.entry().timestamp().toLocalTime().withNano(0).format(TIME), ContextColors.TIMESTAMP);
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
        if (!row.match()) {
            return colored(text, ContextColors.CONTEXT_TEXT);
        }
        if (row.highlights().isEmpty()) {
            return colored(text, ContextColors.MATCH_TEXT);
        }
        MutableComponent result = Component.empty();
        int cursor = 0;
        for (HighlightSpan span : row.highlights()) {
            int highlightStart = Math.clamp(span.start() - start, 0, text.length());
            int highlightEnd = Math.clamp(span.end() - start, 0, text.length());
            if (highlightEnd <= highlightStart) continue;
            if (highlightStart > cursor) {
                result.append(colored(text.substring(cursor, highlightStart), ContextColors.MATCH_TEXT));
            }
            result.append(colored(text.substring(highlightStart, highlightEnd), ContextColors.MATCH_HIGHLIGHT));
            cursor = highlightEnd;
        }
        if (cursor < text.length()) {
            result.append(colored(text.substring(cursor), ContextColors.MATCH_TEXT));
        }
        return result;
    }

    private static Component colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }
}
