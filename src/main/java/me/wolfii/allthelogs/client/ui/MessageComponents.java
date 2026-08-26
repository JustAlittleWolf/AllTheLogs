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
 * Builds chat-line {@link Component}s with hex colours: white for hits, light green on the match, grey for context.
 */
public final class MessageComponents {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);

    private MessageComponents() {
    }

    public static String matchCountText(int matches) {
        return matches > 99 ? ">99" : Integer.toString(matches);
    }

    public static Component timestamp(DisplayRow row) {
        return Component.literal(row.entry().timestamp().toLocalTime().withNano(0).format(TIME))
            .withStyle(Style.EMPTY.withColor(rgb(ContextColors.TIMESTAMP)));
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
            return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb(ContextColors.contextText(row.distanceFromMatch()))));
        }
        if (row.highlights().isEmpty()) {
            return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_TEXT)));
        }
        MutableComponent result = Component.empty();
        int cursor = 0;
        for (HighlightSpan span : row.highlights()) {
            int highlightStart = Math.clamp(span.start() - start, 0, text.length());
            int highlightEnd = Math.clamp(span.end() - start, 0, text.length());
            if (highlightEnd <= 0 || highlightStart >= text.length() || highlightEnd <= highlightStart) {
                continue;
            }
            if (highlightStart > cursor) {
                result.append(Component.literal(text.substring(cursor, highlightStart))
                    .withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_TEXT))));
            }
            result.append(Component.literal(text.substring(highlightStart, highlightEnd))
                .withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_HIGHLIGHT))));
            cursor = highlightEnd;
        }
        if (cursor < text.length()) {
            result.append(Component.literal(text.substring(cursor))
                .withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_TEXT))));
        }
        return result;
    }

    private static int rgb(int argb) {
        return argb & 0xFFFFFF;
    }
}
