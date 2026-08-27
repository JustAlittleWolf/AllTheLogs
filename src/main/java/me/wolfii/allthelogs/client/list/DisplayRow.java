package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

/**
 * One row in the log browser: a stored chat line plus whether it is a search hit and how far it is from the
 * nearest hit in the same log.
 */
public record DisplayRow(
    ChatEntry entry,
    boolean match,
    Duration distanceFromMatch,
    List<HighlightSpan> highlights,
    String message,
    long[] visualFormatting
) {
    public DisplayRow {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(distanceFromMatch, "distanceFromMatch");
        Objects.requireNonNull(highlights, "highlights");
        Objects.requireNonNull(message, "message");
        highlights = List.copyOf(highlights);
        if (distanceFromMatch.isNegative()) {
            throw new IllegalArgumentException("distanceFromMatch must not be negative");
        }
    }

    public DisplayRow(ChatEntry entry, boolean match, Duration distanceFromMatch, List<HighlightSpan> highlights) {
        this(entry, match, distanceFromMatch, highlights, VisualMessage.prepare(entry));
    }

    private DisplayRow(ChatEntry entry, boolean match, Duration distanceFromMatch, List<HighlightSpan> highlights,
                       VisualMessage.Prepared prepared) {
        this(entry, match, distanceFromMatch, highlights, prepared.text(), prepared.formatting());
    }

    /**
     * Marks query results as search hits or context, and measures how far each context line is from the nearest
     * hit in the same log.
     */
    public static List<DisplayRow> from(List<ChatEntry> entries, SearchFilter filter) {
        if (entries.isEmpty()) return List.of();
        if (!filter.hasText()) {
            return entries.stream()
                .map(entry -> new DisplayRow(entry, true, Duration.ZERO, List.of()))
                .toList();
        }

        Predicate<String> matches = filter.messagePredicate();
        Map<ChatLog, List<ChatEntry>> hitsByLog = new HashMap<>();
        for (ChatEntry entry : entries) {
            if (matches.test(entry.message())) {
                hitsByLog.computeIfAbsent(entry.chatLog(), key -> new ArrayList<>()).add(entry);
            }
        }

        return entries.stream().map(entry -> {
            List<ChatEntry> hits = hitsByLog.get(entry.chatLog());
            boolean match = hits != null && hits.contains(entry);
            Duration distance = match ? Duration.ZERO : distanceToNearestHit(entry, hits);
            VisualMessage.Prepared prepared = VisualMessage.prepare(entry);
            List<HighlightSpan> highlights = match ? MatchSpans.spans(prepared.text(), filter) : List.of();
            return new DisplayRow(entry, match, distance, highlights, prepared);
        }).toList();
    }

    private static Duration distanceToNearestHit(ChatEntry entry, List<ChatEntry> hits) {
        if (hits == null || hits.isEmpty()) {
            return Duration.ofMinutes(15);
        }
        Duration nearest = null;
        for (ChatEntry hit : hits) {
            Duration distance = Duration.between(entry.timestamp(), hit.timestamp()).abs();
            if (nearest == null || distance.compareTo(nearest) < 0) {
                nearest = distance;
            }
        }
        return nearest;
    }

    public ChatLog chatLog() {
        return entry.chatLog();
    }

    public int lineIndex() {
        return entry.lineIndex();
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
