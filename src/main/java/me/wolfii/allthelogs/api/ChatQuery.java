package me.wolfii.allthelogs.api;

import java.time.LocalDateTime;

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
public interface ChatQuery {
    /**
     * A query matching every stored entry, ordered by timestamp ascending.
     */
    static ChatQuery all() {
        return me.wolfii.allthelogs.data.ChatQuery.all();
    }

    /**
     * Text every matching message must contain, or {@code null} for no substring filter.
     */
    String substring();

    /**
     * Whether {@link #substring()} is compared case sensitively.
     */
    boolean caseSensitive();

    /**
     * RE2 pattern every matching message must match somewhere, or {@code null}.
     */
    String regex();

    /**
     * Minecraft version the matching entries' logs must have, or {@code null} for any.
     */
    String version();

    /**
     * Earliest timestamp to return, inclusive, or {@code null} for no lower bound.
     */
    LocalDateTime startingAt();

    /**
     * Latest timestamp to return, exclusive, or {@code null} for no upper bound.
     */
    LocalDateTime upUntil();

    /**
     * How many entries to also return either side of every match.
     */
    int contextLines();

    /**
     * Cap on matches, not counting context lines; negative means no cap.
     */
    long limit();

    /**
     * Result order.
     */
    Sort sort();

    /**
     * Exclusive timestamp cursor the page starts after, or {@code null} to start at the end.
     */
    LocalDateTime offset();

    /**
     * Matches to drop after filtering and ordering.
     */
    long skip();

    /**
     * Whether this query filters on the message text at all.
     */
    default boolean hasTextFilter() {
        return substring() != null || regex() != null;
    }

    /**
     * Keeps only entries whose message contains {@code substring}, compared case insensitively.
     * Replaces any previously set substring; a substring and a regex can be combined and both must then match.
     */
    ChatQuery withSubstring(String substring);

    /**
     * Like {@link #withSubstring(String)} but comparing case sensitively.
     */
    ChatQuery withSubstringCaseSensitive(String substring);

    /**
     * Keeps only entries whose message matches the given RE2 regular expression anywhere in the message.
     * Use inline flags such as {@code (?i)} for case insensitive matching.
     */
    ChatQuery withRegex(String regex);

    /**
     * Keeps only entries from logs whose {@link ChatLog#minecraftVersion()} is {@code version}.
     * Replaces any previously set version. Context lines come from the same log, so they share this version.
     */
    ChatQuery withVersion(String version);

    /**
     * Keeps only entries whose timestamp is at or after {@code startingAt}, inclusive.
     * Unlike {@link #withOffset(LocalDateTime)}, this bound also clips context lines.
     */
    ChatQuery startingAt(LocalDateTime startingAt);

    /**
     * Keeps only entries whose timestamp is before {@code upUntil}, exclusive.
     * Unlike {@link #withOffset(LocalDateTime)}, this bound also clips context lines.
     */
    ChatQuery upUntil(LocalDateTime upUntil);

    /**
     * Also returns up to {@code contextLines} entries before and after every match, taken from the same log file.
     * Overlapping context windows are merged, so no entry is ever returned twice.
     */
    ChatQuery withContextLines(int contextLines);

    /**
     * Caps the number of matching entries. Context lines are extra and do not count toward the limit.
     * A negative value means no limit.
     */
    ChatQuery withLimit(long limit);

    /**
     * Sets the result order. Ascending is oldest first; descending is newest first.
     */
    ChatQuery withSort(Sort sort);

    /**
     * Starts the page at a timestamp cursor, exclusive, to complement {@link #withLimit(long)}.
     * <p>
     * When sorting ascending, only matches after {@code offset} are kept; when sorting descending, only matches before
     * {@code offset} are kept. Context lines around those matches may still fall on the other side of {@code offset},
     * including at the offset timestamp itself. Combine with a limit by passing the last returned match timestamp as
     * the next page's offset.
     */
    ChatQuery withOffset(LocalDateTime offset);

    /**
     * Skips the first {@code skip} matches after filtering and ordering. Used by the timeline to land inside
     * a cluster of messages that share a timestamp. {@code 0} means no skip.
     */
    ChatQuery withSkip(long skip);

    /**
     * Result order by timestamp, then file id, then line index.
     */
    enum Sort {
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
