package me.wolfii.allthelogs.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;

/**
 * Describes which chat entries to retrieve. Start from {@link #all()} and narrow it down with chained methods;
 * every method returns a new query, so instances are safe to share and reuse.
 * {@snippet :
 * ChatQuery query = ChatQuery.all()
 *         .withRegex("(?i)welcome to")
 *         .withVersion("26.2")
 *         .withServerOrWorld("hypixel.net")
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
     * Stands for a missing value in {@link #withVersion(String)}, {@link #withVersions(Collection)} and
     * {@link #withServersOrWorlds(Collection)}: it keeps entries whose {@link ChatLog#minecraftVersion()} or
     * {@link ChatEntry#serverOrWorld()} is {@code null}. No release or server address starts with a dot.
     */
    String NULL = ".NULL";

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
     * Minecraft version the matching entries' logs must have, {@link #NULL} for logs without one, or
     * {@code null} for any.
     */
    String version();

    /**
     * Server or world substring the matching entries must contain, or {@code null} for any.
     * Compared case insensitively.
     */
    String serverOrWorld();

    /**
     * Minecraft versions the matching entries' logs must have one of, with {@link #NULL} for logs without one,
     * or {@code null} for any. Empty matches nothing.
     */
    Set<String> versions();

    /**
     * Servers or worlds the matching entries must have exactly one of, with {@link #NULL} for entries without
     * one, or {@code null} for any. Empty matches nothing.
     */
    Set<String> serversOrWorlds();

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
     * Log of the exclusive row cursor, or {@code null} when {@link #offset()} is timestamp-only.
     */
    LogSource offsetSource();

    /**
     * Line index of the exclusive row cursor. Ignored when {@link #offsetSource()} is {@code null}.
     */
    int offsetLine();

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
     * Keeps only entries from logs whose {@link ChatLog#minecraftVersion()} is {@code version}; {@link #NULL}
     * keeps logs without one. Replaces any previously set version. Context lines come from the same log, so
     * they share this version.
     */
    ChatQuery withVersion(String version);

    /**
     * Keeps only entries whose {@link ChatEntry#serverOrWorld()} contains {@code serverOrWorld},
     * compared case insensitively. Replaces any previously set server or world. Remote servers use the
     * address; local worlds use {@code world/{name}}. Context lines are taken from servers that also match.
     */
    ChatQuery withServerOrWorld(String serverOrWorld);

    /**
     * Keeps only entries from logs whose {@link ChatLog#minecraftVersion()} is one of {@code versions};
     * {@link #NULL} keeps logs without one. {@code null} removes the restriction and an empty collection
     * matches nothing. Combines with {@link #withVersion(String)}: both must hold.
     */
    ChatQuery withVersions(Collection<String> versions);

    /**
     * Keeps only entries whose {@link ChatEntry#serverOrWorld()} is exactly one of {@code serversOrWorlds};
     * {@link #NULL} keeps entries without one. {@code null} removes the restriction and an
     * empty collection matches nothing. Context lines are taken from the same servers. Combines with
     * {@link #withServerOrWorld(String)}: both must hold.
     */
    ChatQuery withServersOrWorlds(Collection<String> serversOrWorlds);

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
     * Also returns up to {@code contextLines} entries before and after every match, taken from the same log file
     * and still passing the date and server filters. Neighbours that fail those filters are skipped so the
     * requested count is filled with matching context.
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
     * including at the offset timestamp itself.
     * <p>
     * This bound is exclusive on the whole timestamp, so later matches that share {@code offset} are dropped.
     * To continue after a specific line without losing the rest of that second, use
     * {@link #withOffset(LocalDateTime, LogSource, int)}.
     */
    ChatQuery withOffset(LocalDateTime offset);

    /**
     * Starts the page after this exact line, exclusive, keeping other matches that share {@code offset}.
     * <p>
     * Sort order is timestamp, then file, then line index, so a later (or earlier) line in the same
     * second is still returned. Clears any previous timestamp-only offset.
     */
    ChatQuery withOffset(LocalDateTime offset, LogSource source, int lineIndex);

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
