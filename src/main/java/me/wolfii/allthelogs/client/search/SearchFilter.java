package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.data.ChatQuery;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * UI-facing description of a log search. Converted to a {@link ChatQuery} by {@link ChatQueryFactory}.
 */
public final class SearchFilter {
    public static final int MAX_CONTEXT_LINES = 1000;
    public static final int DEFAULT_LIMIT = 100;
    public static final int DEFAULT_CONTEXT_LINES = 2;

    private final String text;
    private final boolean regex;
    private final boolean caseSensitive;
    private final int contextLines;
    private final long limit;
    private final ChatQuery.Sort sort;
    private final LocalDateTime startingAt;
    private final LocalDateTime upUntil;
    private final LocalDateTime offset;

    private SearchFilter(String text, boolean regex, boolean caseSensitive, int contextLines, long limit,
                         ChatQuery.Sort sort, LocalDateTime startingAt, LocalDateTime upUntil, LocalDateTime offset) {
        this.text = text;
        this.regex = regex;
        this.caseSensitive = caseSensitive;
        this.contextLines = contextLines;
        this.limit = limit;
        this.sort = sort;
        this.startingAt = startingAt;
        this.upUntil = upUntil;
        this.offset = offset;
    }

    public static SearchFilter defaults() {
        return new SearchFilter("", false, false, DEFAULT_CONTEXT_LINES, DEFAULT_LIMIT, ChatQuery.Sort.ASCENDING,
            null, null, null);
    }

    public SearchFilter withText(String text) {
        Objects.requireNonNull(text, "text");
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withRegex(boolean regex) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withCaseSensitive(boolean caseSensitive) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withContextLines(int contextLines) {
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        if (contextLines > MAX_CONTEXT_LINES) {
            throw new IllegalArgumentException("contextLines must be at most " + MAX_CONTEXT_LINES);
        }
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withLimit(long limit) {
        if (limit == 0) throw new IllegalArgumentException("limit must not be zero");
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withSort(ChatQuery.Sort sort) {
        Objects.requireNonNull(sort, "sort");
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withStartingAt(LocalDateTime startingAt) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withUpUntil(LocalDateTime upUntil) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withOffset(LocalDateTime offset) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }

    public SearchFilter withoutOffset() {
        return withOffset(null);
    }

    public String text() {
        return text;
    }

    public boolean regex() {
        return regex;
    }

    public boolean caseSensitive() {
        return caseSensitive;
    }

    public int contextLines() {
        return contextLines;
    }

    public long limit() {
        return limit;
    }

    public ChatQuery.Sort sort() {
        return sort;
    }

    public LocalDateTime startingAt() {
        return startingAt;
    }

    public LocalDateTime upUntil() {
        return upUntil;
    }

    public LocalDateTime offset() {
        return offset;
    }

    public boolean hasText() {
        return text != null && !text.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SearchFilter other)) return false;
        return regex == other.regex
            && caseSensitive == other.caseSensitive
            && contextLines == other.contextLines
            && limit == other.limit
            && sort == other.sort
            && Objects.equals(text, other.text)
            && Objects.equals(startingAt, other.startingAt)
            && Objects.equals(upUntil, other.upUntil)
            && Objects.equals(offset, other.offset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset);
    }
}
