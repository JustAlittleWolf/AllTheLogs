package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.data.ChatQuery;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Browser search: text, regex, dates, sort, and paging. {@link #toQuery()} is the store query;
 * {@link #messagePredicate()} marks hits after the query returns.
 */
public record SearchFilter(
    String text,
    boolean regex,
    boolean caseSensitive,
    int contextLines,
    long limit,
    ChatQuery.Sort sort,
    LocalDateTime startingAt,
    LocalDateTime upUntil,
    LocalDateTime offset,
    String version
) {
    public static final int MAX_CONTEXT_LINES = 1000;
    public static final int DEFAULT_LIMIT = 100;
    public static final int DEFAULT_CONTEXT_LINES = 4;
    public static final String ALL_VERSIONS = "ALL";

    public SearchFilter {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sort, "sort");
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        if (contextLines > MAX_CONTEXT_LINES) {
            throw new IllegalArgumentException("contextLines must be at most " + MAX_CONTEXT_LINES);
        }
        if (limit == 0) throw new IllegalArgumentException("limit must not be zero");
        if (version != null && version.isBlank()) version = null;
        if (version != null && ALL_VERSIONS.equalsIgnoreCase(version)) version = null;
    }

    public static SearchFilter defaults() {
        return new SearchFilter("", false, false, DEFAULT_CONTEXT_LINES, DEFAULT_LIMIT, ChatQuery.Sort.ASCENDING,
            null, null, null, null);
    }

    public SearchFilter withText(String text) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withRegex(boolean regex) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withCaseSensitive(boolean caseSensitive) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withContextLines(int contextLines) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withLimit(long limit) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withSort(ChatQuery.Sort sort) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withStartingAt(LocalDateTime startingAt) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withUpUntil(LocalDateTime upUntil) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withOffset(LocalDateTime offset) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withVersion(String version) {
        return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt, upUntil, offset,
            version);
    }

    public SearchFilter withoutOffset() {
        return withOffset(null);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    public boolean hasVersion() {
        return version != null && !version.isEmpty();
    }

    /**
     * Store query for this filter. Empty text means every entry; regex uses DuckDB RE2 with an inline
     * {@code (?i)} flag when the search is case insensitive.
     */
    public ChatQuery toQuery() {
        ChatQuery query = ChatQuery.all()
            .withContextLines(contextLines)
            .withLimit(limit)
            .withSort(sort);
        if (startingAt != null) query = query.startingAt(startingAt);
        if (upUntil != null) query = query.upUntil(upUntil);
        if (offset != null) query = query.withOffset(offset);
        if (hasVersion()) query = query.withVersion(version);
        if (!hasText()) return query;
        if (regex) return query.withRegex(regexPattern(text, caseSensitive));
        if (caseSensitive) return query.withSubstringCaseSensitive(text);
        return query.withSubstring(text);
    }

    /**
     * Same as {@link #toQuery()} but without context lines, a page limit, or an offset, for timeline bounds.
     */
    public ChatQuery toTimelineQuery() {
        return withoutOffset().withContextLines(0).withLimit(-1).toQuery();
    }

    /**
     * Java-side matching used to mark hits after a query. Invalid regex never matches.
     */
    public Predicate<String> messagePredicate() {
        if (!hasText()) return message -> true;
        if (regex) {
            return compiledRegex(text, caseSensitive)
                .map(pattern -> (Predicate<String>) message -> pattern.matcher(message).find())
                .orElse(message -> false);
        }
        if (caseSensitive) {
            String needle = text;
            return message -> message.contains(needle);
        }
        String needle = text.toLowerCase(Locale.ROOT);
        return message -> message.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static Optional<Pattern> compiledRegex(String regex, boolean caseSensitive) {
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            return Optional.of(Pattern.compile(regex, flags));
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }

    static String regexPattern(String regex, boolean caseSensitive) {
        if (caseSensitive || hasInlineCaseFlag(regex)) return regex;
        return "(?i)" + regex;
    }

    private static boolean hasInlineCaseFlag(String regex) {
        return regex.startsWith("(?i)") || regex.startsWith("(?-i)") || regex.startsWith("(?iu)");
    }
}
