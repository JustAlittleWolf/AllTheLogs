package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.data.ChatQuery;

/**
 * Turns a {@link SearchFilter} into a {@link ChatQuery}.
 */
public final class ChatQueryFactory {
    private ChatQueryFactory() {
    }

    public static ChatQuery toQuery(SearchFilter filter) {
        ChatQuery query = ChatQuery.all()
            .withContextLines(filter.contextLines())
            .withLimit(filter.limit())
            .withSort(filter.sort());
        if (filter.startingAt() != null) {
            query = query.startingAt(filter.startingAt());
        }
        if (filter.upUntil() != null) {
            query = query.upUntil(filter.upUntil());
        }
        if (filter.offset() != null) {
            query = query.withOffset(filter.offset());
        }
        if (!filter.hasText()) {
            return query;
        }
        if (filter.regex()) {
            return query.withRegex(regexPattern(filter.text(), filter.caseSensitive()));
        }
        if (filter.caseSensitive()) {
            return query.withSubstringCaseSensitive(filter.text());
        }
        return query.withSubstring(filter.text());
    }

    /**
     * Same filter as {@link #toQuery(SearchFilter)} but without context lines or a page limit, for timeline markers.
     */
    public static ChatQuery toTimelineQuery(SearchFilter filter) {
        return toQuery(filter.withoutOffset().withContextLines(0).withLimit(-1));
    }

    static String regexPattern(String regex, boolean caseSensitive) {
        if (caseSensitive || hasInlineCaseFlag(regex)) {
            return regex;
        }
        return "(?i)" + regex;
    }

    private static boolean hasInlineCaseFlag(String regex) {
        return regex.startsWith("(?i)") || regex.startsWith("(?-i)") || regex.startsWith("(?iu)");
    }
}
