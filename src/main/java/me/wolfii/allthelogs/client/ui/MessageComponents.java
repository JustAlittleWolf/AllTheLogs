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
        String text = row.message();
        if (!row.match()) {
            return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb(ContextColors.contextText(row.distanceFromMatch()))));
        }
        if (row.highlights().isEmpty()) {
            return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_TEXT)));
        }
        MutableComponent result = Component.empty();
        int cursor = 0;
        for (HighlightSpan span : row.highlights()) {
            int start = Math.min(span.start(), text.length());
            int end = Math.min(span.end(), text.length());
            if (start > cursor) {
                result.append(Component.literal(text.substring(cursor, start))
                    .withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_TEXT))));
            }
            if (end > start) {
                result.append(Component.literal(text.substring(start, end))
                    .withStyle(Style.EMPTY.withColor(rgb(ContextColors.MATCH_HIGHLIGHT))));
            }
            cursor = Math.max(cursor, end);
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
