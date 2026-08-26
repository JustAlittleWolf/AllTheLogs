package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Describes which chat entries to retrieve. Start from {@link #all()} and narrow it down with the {@code with*}
 * methods; every method returns a new query, so instances are safe to share and reuse.
 * {@snippet :
 * ChatQuery query = ChatQuery.all()
 *         .withRegex("(?i)welcome to")
 *         .withRange(from, to)
 *         .withContextLines(2);
 * }
 */
public final class ChatQuery {
    private final String substring;
    private final boolean caseSensitive;
    private final String regex;
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final int contextLines;
    private final long limit;
    private final boolean descending;

    private ChatQuery(String substring, boolean caseSensitive, String regex, LocalDateTime from, LocalDateTime to,
                      int contextLines, long limit, boolean descending) {
        this.substring = substring;
        this.caseSensitive = caseSensitive;
        this.regex = regex;
        this.from = from;
        this.to = to;
        this.contextLines = contextLines;
        this.limit = limit;
        this.descending = descending;
    }

    /**
     * A query matching every stored entry, ordered by timestamp ascending.
     */
    public static ChatQuery all() {
        return new ChatQuery(null, false, null, null, null, 0, -1, false);
    }

    /**
     * Keeps only entries whose message contains {@code substring}, compared case insensitively.
     * Replaces any previously set substring; a substring and a regex can be combined and both must then match.
     */
    public ChatQuery withSubstring(String substring) {
        Objects.requireNonNull(substring, "substring");
        return new ChatQuery(substring, false, regex, from, to, contextLines, limit, descending);
    }

    /**
     * Like {@link #withSubstring(String)} but comparing case sensitively.
     */
    public ChatQuery withSubstringCaseSensitive(String substring) {
        Objects.requireNonNull(substring, "substring");
        return new ChatQuery(substring, true, regex, from, to, contextLines, limit, descending);
    }

    /**
     * Keeps only entries whose message matches the given RE2 regular expression anywhere in the message.
     * Use inline flags such as {@code (?i)} for case insensitive matching.
     */
    public ChatQuery withRegex(String regex) {
        Objects.requireNonNull(regex, "regex");
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, descending);
    }

    /**
     * Keeps only entries whose timestamp lies in {@code [from, to)}. Either bound may be {@code null} to leave that
     * side open.
     */
    public ChatQuery withRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from " + from + " is after to " + to);
        }
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, descending);
    }

    /**
     * Also returns up to {@code contextLines} entries before and after every match, taken from the same log file.
     * Overlapping context windows are merged, so no entry is ever returned twice.
     */
    public ChatQuery withContextLines(int contextLines) {
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, descending);
    }

    /**
     * Caps the number of returned entries, including context lines. A negative value means no limit.
     */
    public ChatQuery withLimit(long limit) {
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, descending);
    }

    /**
     * Returns entries newest first instead of oldest first.
     */
    public ChatQuery withDescending(boolean descending) {
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, descending);
    }

    public String substring() {
        return substring;
    }

    public boolean caseSensitive() {
        return caseSensitive;
    }

    public String regex() {
        return regex;
    }

    public LocalDateTime from() {
        return from;
    }

    public LocalDateTime to() {
        return to;
    }

    public int contextLines() {
        return contextLines;
    }

    public long limit() {
        return limit;
    }

    public boolean descending() {
        return descending;
    }

    /**
     * Whether this query filters on the message text at all.
     */
    public boolean hasTextFilter() {
        return substring != null || regex != null;
    }

    @Override
    public String toString() {
        return "ChatQuery[substring=" + substring + ", caseSensitive=" + caseSensitive + ", regex=" + regex
            + ", from=" + from + ", to=" + to + ", contextLines=" + contextLines + ", limit=" + limit
            + ", descending=" + descending + "]";
    }
}
