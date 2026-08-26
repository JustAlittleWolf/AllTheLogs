package me.wolfii.allthelogs.runtime;

import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Marks query results as search hits or context, and measures how far each context line is from the nearest hit
 * in the same log.
 */
public final class EntryClassifier {
    private EntryClassifier() {
    }

    public static List<DisplayRow> classify(List<ChatEntry> entries, SearchFilter filter) {
        if (entries.isEmpty()) return List.of();
        if (!filter.hasText()) {
            List<DisplayRow> rows = new ArrayList<>(entries.size());
            for (ChatEntry entry : entries) {
                rows.add(new DisplayRow(entry, true, Duration.ZERO, List.of()));
            }
            return List.copyOf(rows);
        }

        Predicate<String> matches = matcher(filter);
        List<ChatEntry> hits = new ArrayList<>();
        for (ChatEntry entry : entries) {
            if (matches.test(entry.message())) {
                hits.add(entry);
            }
        }

        Map<ChatLog, List<ChatEntry>> hitsByLog = new HashMap<>();
        for (ChatEntry hit : hits) {
            hitsByLog.computeIfAbsent(hit.chatLog(), key -> new ArrayList<>()).add(hit);
        }

        List<DisplayRow> rows = new ArrayList<>(entries.size());
        for (ChatEntry entry : entries) {
            boolean match = matches.test(entry.message());
            Duration distance = match ? Duration.ZERO : distanceToNearestHit(entry, hitsByLog.get(entry.chatLog()));
            List<HighlightSpan> highlights = match ? MatchHighlighter.spans(entry.message(), filter) : List.of();
            rows.add(new DisplayRow(entry, match, distance, highlights));
        }
        return List.copyOf(rows);
    }

    static Predicate<String> matcher(SearchFilter filter) {
        if (filter.regex()) {
            try {
                int flags = filter.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                Pattern pattern = Pattern.compile(filter.text(), flags);
                return message -> pattern.matcher(message).find();
            } catch (PatternSyntaxException e) {
                return message -> false;
            }
        }
        if (filter.caseSensitive()) {
            String needle = filter.text();
            return message -> message.contains(needle);
        }
        String needle = filter.text().toLowerCase(Locale.ROOT);
        return message -> message.toLowerCase(Locale.ROOT).contains(needle);
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
}
