package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;
import java.util.Objects;

/// Describes which chat entries to retrieve. Start from [#all()] and narrow it down with the `with*` methods; every
/// method returns a new query, so instances are safe to share and reuse.
///
/// ```java
/// ChatQuery query = ChatQuery.all()
///         .withRegex("(?i)welcome to")
///         .withRange(from, to)
///         .withSort(Sort.DESCENDING)
///         .withOffset(lastSeen)
///         .withLimit(100)
///         .withContextLines(2);
/// ```
public final class ChatQuery {
    /// Result order by timestamp, then file id, then line index.
    public enum Sort {
        /// Oldest first; the default.
        ASCENDING,
        /// Newest first.
        DESCENDING
    }

    private final String substring;
    private final boolean caseSensitive;
    private final String regex;
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final int contextLines;
    private final long limit;
    private final Sort sort;
    private final LocalDateTime offset;

    private ChatQuery(String substring, boolean caseSensitive, String regex, LocalDateTime from, LocalDateTime to,
                      int contextLines, long limit, Sort sort, LocalDateTime offset) {
        this.substring = substring;
        this.caseSensitive = caseSensitive;
        this.regex = regex;
        this.from = from;
        this.to = to;
        this.contextLines = contextLines;
        this.limit = limit;
        this.sort = sort;
        this.offset = offset;
    }

    /// A query matching every stored entry, ordered by timestamp ascending.
    public static ChatQuery all() {
        return new ChatQuery(null, false, null, null, null, 0, -1, Sort.ASCENDING, null);
    }

    /// Keeps only entries whose message contains `substring`, compared case insensitively.
    /// Replaces any previously set substring; a substring and a regex can be combined and both must then match.
    public ChatQuery withSubstring(String substring) {
        Objects.requireNonNull(substring, "substring");
        return new ChatQuery(substring, false, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Like [#withSubstring(String)] but comparing case sensitively.
    public ChatQuery withSubstringCaseSensitive(String substring) {
        Objects.requireNonNull(substring, "substring");
        return new ChatQuery(substring, true, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Keeps only entries whose message matches the given RE2 regular expression anywhere in the message.
    /// Use inline flags such as `(?i)` for case insensitive matching.
    public ChatQuery withRegex(String regex) {
        Objects.requireNonNull(regex, "regex");
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Keeps only entries whose timestamp lies in `[from, to)`. Either bound may be `null` to leave that side open.
    /// Unlike [#withOffset(LocalDateTime)], this bound also clips context lines.
    public ChatQuery withRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from " + from + " is after to " + to);
        }
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Also returns up to `contextLines` entries before and after every match, taken from the same log file.
    /// Overlapping context windows are merged, so no entry is ever returned twice.
    public ChatQuery withContextLines(int contextLines) {
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Caps the number of matching entries. Context lines are extra and do not count toward the limit.
    /// A negative value means no limit.
    public ChatQuery withLimit(long limit) {
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Sets the result order. Ascending is oldest first; descending is newest first.
    /// Replaces any previously set sort, including one chosen with [#withDescending(boolean)].
    public ChatQuery withSort(Sort sort) {
        Objects.requireNonNull(sort, "sort");
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, sort, offset);
    }

    /// Returns entries newest first instead of oldest first. Equivalent to
    /// `withSort(descending ? Sort.DESCENDING : Sort.ASCENDING)`.
    public ChatQuery withDescending(boolean descending) {
        return withSort(descending ? Sort.DESCENDING : Sort.ASCENDING);
    }

    /// Starts the page at a timestamp cursor, exclusive, to complement [#withLimit(long)].
    ///
    /// When sorting ascending, only matches after `offset` are kept; when sorting descending, only matches before
    /// `offset` are kept. Context lines around those matches may still fall on the other side of `offset`, including
    /// at the offset timestamp itself. Combine with a limit by passing the last returned match timestamp as the next
    /// page's offset.
    public ChatQuery withOffset(LocalDateTime offset) {
        Objects.requireNonNull(offset, "offset");
        return new ChatQuery(substring, caseSensitive, regex, from, to, contextLines, limit, sort, offset);
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

    public Sort sort() {
        return sort;
    }

    public boolean descending() {
        return sort == Sort.DESCENDING;
    }

    public LocalDateTime offset() {
        return offset;
    }

    /// Whether this query filters on the message text at all.
    public boolean hasTextFilter() {
        return substring != null || regex != null;
    }

    @Override
    public String toString() {
        return "ChatQuery[substring=" + substring + ", caseSensitive=" + caseSensitive + ", regex=" + regex
            + ", from=" + from + ", to=" + to + ", contextLines=" + contextLines + ", limit=" + limit
            + ", sort=" + sort + ", offset=" + offset + "]";
    }
}
