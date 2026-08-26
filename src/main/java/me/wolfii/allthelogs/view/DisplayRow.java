package me.wolfii.allthelogs.view;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * One row in the log browser: a stored chat line plus whether it is a search hit and how far it is from the
 * nearest hit in the same log (used to grey out context).
 */
public record DisplayRow(
    ChatEntry entry,
    boolean match,
    Duration distanceFromMatch,
    List<HighlightSpan> highlights
) {
    public DisplayRow {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(distanceFromMatch, "distanceFromMatch");
        Objects.requireNonNull(highlights, "highlights");
        highlights = List.copyOf(highlights);
        if (distanceFromMatch.isNegative()) {
            throw new IllegalArgumentException("distanceFromMatch must not be negative");
        }
    }

    public ChatLog chatLog() {
        return entry.chatLog();
    }

    public int lineIndex() {
        return entry.lineIndex();
    }

    public String message() {
        return entry.message();
    }

    public RowKey key() {
        return new RowKey(chatLog(), lineIndex());
    }

    public record RowKey(ChatLog chatLog, int lineIndex) {
        public RowKey {
            Objects.requireNonNull(chatLog, "chatLog");
        }
    }
}
