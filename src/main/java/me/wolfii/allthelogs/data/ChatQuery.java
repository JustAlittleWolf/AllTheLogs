package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Describes which chat entries to retrieve. Start from {@link #all()} and narrow it down with chained methods;
 * every method returns a new query, so instances are safe to share and reuse.
 * {@snippet :
 * ChatQuery query = ChatQuery.all()
 *         .withRegex("(?i)welcome to")
 *         .withVersion("26.2")
 *         .startingAt(from)
 *         .upUntil(to)
 *         .withSort(Sort.DESCENDING)
 *         .withOffset(lastSeen)
 *         .withLimit(100)
 *         .withContextLines(2);
 *}
 */
public record ChatQuery(
    String substring,
    boolean caseSensitive,
    String regex,
    String version,
    LocalDateTime startingAt,
    LocalDateTime upUntil,
    int contextLines,
    long limit,
    Sort sort,
    LocalDateTime offset
) {
    public ChatQuery {
        Objects.requireNonNull(sort, "sort");
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        requireOrderedBounds(startingAt, upUntil);
    }

    /**
     * A query matching every stored entry, ordered by timestamp ascending.
     */
    public static ChatQuery all() {
        return new ChatQuery(null, false, null, null, null, null, 0, -1, Sort.ASCENDING, null);
    }

    private static void requireOrderedBounds(LocalDateTime startingAt, LocalDateTime upUntil) {
        if (startingAt != null && upUntil != null && startingAt.isAfter(upUntil)) {
            throw new IllegalArgumentException("startingAt " + startingAt + " is after upUntil " + upUntil);
        }
    }

    /**
     * Keeps only entries whose message contains {@code substring}, compared case insensitively.
     * Replaces any previously set substring; a substring and a regex can be combined and both must then match.
     */
    public ChatQuery withSubstring(String substring) {
        Objects.requireNonNull(substring, "substring");
        return new ChatQuery(substring, false, regex, version, startingAt, upUntil, contextLines, limit, sort, offset);
    }

    /**
     * Like {@link #withSubstring(String)} but comparing case sensitively.
     */
    public ChatQuery withSubstringCaseSensitive(String substring) {
        Objects.requireNonNull(substring, "substring");
        return new ChatQuery(substring, true, regex, version, startingAt, upUntil, contextLines, limit, sort, offset);
    }

    /**
     * Keeps only entries whose message matches the given RE2 regular expression anywhere in the message.
     * Use inline flags such as {@code (?i)} for case insensitive matching.
     */
    public ChatQuery withRegex(String regex) {
        Objects.requireNonNull(regex, "regex");
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Keeps only entries from logs whose {@link ChatLog#minecraftVersion()} is {@code version}.
     * Replaces any previously set version. Context lines come from the same log, so they share this version.
     */
    public ChatQuery withVersion(String version) {
        Objects.requireNonNull(version, "version");
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Keeps only entries whose timestamp is at or after {@code startingAt}, inclusive.
     * Unlike {@link #withOffset(LocalDateTime)}, this bound also clips context lines.
     */
    public ChatQuery startingAt(LocalDateTime startingAt) {
        Objects.requireNonNull(startingAt, "startingAt");
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Keeps only entries whose timestamp is before {@code upUntil}, exclusive.
     * Unlike {@link #withOffset(LocalDateTime)}, this bound also clips context lines.
     */
    public ChatQuery upUntil(LocalDateTime upUntil) {
        Objects.requireNonNull(upUntil, "upUntil");
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Also returns up to {@code contextLines} entries before and after every match, taken from the same log file.
     * Overlapping context windows are merged, so no entry is ever returned twice.
     */
    public ChatQuery withContextLines(int contextLines) {
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Caps the number of matching entries. Context lines are extra and do not count toward the limit.
     * A negative value means no limit.
     */
    public ChatQuery withLimit(long limit) {
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Sets the result order. Ascending is oldest first; descending is newest first.
     */
    public ChatQuery withSort(Sort sort) {
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Starts the page at a timestamp cursor, exclusive, to complement {@link #withLimit(long)}.
     * <p>
     * When sorting ascending, only matches after {@code offset} are kept; when sorting descending, only matches before
     * {@code offset} are kept. Context lines around those matches may still fall on the other side of {@code offset},
     * including at the offset timestamp itself. Combine with a limit by passing the last returned match timestamp as
     * the next page's offset.
     */
    public ChatQuery withOffset(LocalDateTime offset) {
        Objects.requireNonNull(offset, "offset");
        return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines, limit, sort,
            offset);
    }

    /**
     * Whether this query filters on the message text at all.
     */
    public boolean hasTextFilter() {
        return substring != null || regex != null;
    }

    /**
     * Result order by timestamp, then file id, then line index.
     */
    public enum Sort {
        /**
         * Oldest first; the default.
         */
        ASCENDING,
        /**
         * Newest first.
         */
        DESCENDING;

        public Sort opposite() {
            return this == ASCENDING ? DESCENDING : ASCENDING;
        }
    }
}
