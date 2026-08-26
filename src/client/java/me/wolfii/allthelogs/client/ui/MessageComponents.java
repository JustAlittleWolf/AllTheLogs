package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.runtime.ContextColors;
import me.wolfii.allthelogs.runtime.DisplayRow;
import me.wolfii.allthelogs.runtime.HighlightSpan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Builds chat-line {@link Component}s with hex colours: white for hits, light green on the match, grey for context.
 */
public final class MessageComponents {
    private MessageComponents() {
    }

    public static Component timestamp(DisplayRow row) {
        return Component.literal(row.entry().timestamp().toString().replace('T', ' '))
            .withStyle(Style.EMPTY.withColor(rgb(ContextColors.TIMESTAMP)));
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
