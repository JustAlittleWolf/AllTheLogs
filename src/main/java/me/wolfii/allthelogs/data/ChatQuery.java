package me.wolfii.allthelogs.data;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Describes which chat entries to retrieve. Start from {@link #all()} and narrow it down with chained methods;
 * every method returns a new query, so instances are safe to share and reuse.
 *
 * @param substring     text every matching message must contain, or {@code null} for no substring filter
 * @param caseSensitive whether {@code substring} is compared case sensitively
 * @param regex         RE2 pattern every matching message must match somewhere, or {@code null}
 * @param version       Minecraft version the matching entries' logs must have, or {@code null} for any
 * @param startingAt    earliest timestamp to return, inclusive, or {@code null} for no lower bound
 * @param upUntil       latest timestamp to return, exclusive, or {@code null} for no upper bound
 * @param contextLines  how many entries to also return either side of every match
 * @param limit         cap on matches, not counting context lines; negative means no cap
 * @param sort          result order
 * @param offset        exclusive timestamp cursor the page starts after, or {@code null} to start at the end
 * @param skip          matches to drop after filtering and ordering
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
    LocalDateTime offset,
    long skip
) implements me.wolfii.allthelogs.api.ChatQuery {
    public ChatQuery {
        Objects.requireNonNull(sort, "sort");
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        if (skip < 0) throw new IllegalArgumentException("skip must not be negative");
        if (startingAt != null && upUntil != null && startingAt.isAfter(upUntil)) {
            throw new IllegalArgumentException("startingAt " + startingAt + " is after upUntil " + upUntil);
        }
    }

    /**
     * A query matching every stored entry, ordered by timestamp ascending.
     */
    public static ChatQuery all() {
        return new ChatQuery(null, false, null, null, null, null, 0, -1, Sort.ASCENDING, null, 0);
    }

    /**
     * Keeps only entries whose message contains {@code substring}, compared case insensitively.
     * Replaces any previously set substring; a substring and a regex can be combined and both must then match.
     */
    @Override
    public ChatQuery withSubstring(String substring) {
        Objects.requireNonNull(substring, "substring");
        return with(draft -> {
            draft.substring = substring;
            draft.caseSensitive = false;
        });
    }

    /**
     * Like {@link #withSubstring(String)} but comparing case sensitively.
     */
    @Override
    public ChatQuery withSubstringCaseSensitive(String substring) {
        Objects.requireNonNull(substring, "substring");
        return with(draft -> {
            draft.substring = substring;
            draft.caseSensitive = true;
        });
    }

    /**
     * Keeps only entries whose message matches the given RE2 regular expression anywhere in the message.
     * Use inline flags such as {@code (?i)} for case insensitive matching.
     */
    @Override
    public ChatQuery withRegex(String regex) {
        Objects.requireNonNull(regex, "regex");
        return with(draft -> draft.regex = regex);
    }

    /**
     * Keeps only entries from logs whose {@link ChatLog#minecraftVersion()} is {@code version}.
     * Replaces any previously set version. Context lines come from the same log, so they share this version.
     */
    @Override
    public ChatQuery withVersion(String version) {
        Objects.requireNonNull(version, "version");
        return with(draft -> draft.version = version);
    }

    /**
     * Keeps only entries whose timestamp is at or after {@code startingAt}, inclusive.
     * Unlike {@link #withOffset(LocalDateTime)}, this bound also clips context lines.
     */
    @Override
    public ChatQuery startingAt(LocalDateTime startingAt) {
        Objects.requireNonNull(startingAt, "startingAt");
        return with(draft -> draft.startingAt = startingAt);
    }

    /**
     * Keeps only entries whose timestamp is before {@code upUntil}, exclusive.
     * Unlike {@link #withOffset(LocalDateTime)}, this bound also clips context lines.
     */
    @Override
    public ChatQuery upUntil(LocalDateTime upUntil) {
        Objects.requireNonNull(upUntil, "upUntil");
        return with(draft -> draft.upUntil = upUntil);
    }

    /**
     * Also returns up to {@code contextLines} entries before and after every match, taken from the same log file.
     * Overlapping context windows are merged, so no entry is ever returned twice.
     */
    @Override
    public ChatQuery withContextLines(int contextLines) {
        return with(draft -> draft.contextLines = contextLines);
    }

    /**
     * Caps the number of matching entries. Context lines are extra and do not count toward the limit.
     * A negative value means no limit.
     */
    @Override
    public ChatQuery withLimit(long limit) {
        return with(draft -> draft.limit = limit);
    }

    /**
     * Sets the result order. Ascending is oldest first; descending is newest first.
     */
    @Override
    public ChatQuery withSort(Sort sort) {
        return with(draft -> draft.sort = sort);
    }

    /**
     * Starts the page at a timestamp cursor, exclusive, to complement {@link #withLimit(long)}.
     * <p>
     * When sorting ascending, only matches after {@code offset} are kept; when sorting descending, only matches before
     * {@code offset} are kept. Context lines around those matches may still fall on the other side of {@code offset},
     * including at the offset timestamp itself. Combine with a limit by passing the last returned match timestamp as
     * the next page's offset.
     */
    @Override
    public ChatQuery withOffset(LocalDateTime offset) {
        Objects.requireNonNull(offset, "offset");
        return with(draft -> draft.offset = offset);
    }

    /**
     * Skips the first {@code skip} matches after filtering and ordering. Used by the timeline to land inside
     * a cluster of messages that share a timestamp. {@code 0} means no skip.
     */
    @Override
    public ChatQuery withSkip(long skip) {
        return with(draft -> draft.skip = skip);
    }

    /**
     * Copies this query with {@code change} applied, so each {@code with*} method only has to name the fields
     * it actually touches.
     */
    private ChatQuery with(Consumer<Draft> change) {
        Draft draft = new Draft(this);
        change.accept(draft);
        return draft.build();
    }

    /**
     * Mutable copy of a query, used only between a {@code with*} call and the record it builds.
     */
    private static final class Draft {
        private String substring;
        private boolean caseSensitive;
        private String regex;
        private String version;
        private LocalDateTime startingAt;
        private LocalDateTime upUntil;
        private int contextLines;
        private long limit;
        private Sort sort;
        private LocalDateTime offset;
        private long skip;

        private Draft(ChatQuery query) {
            this.substring = query.substring;
            this.caseSensitive = query.caseSensitive;
            this.regex = query.regex;
            this.version = query.version;
            this.startingAt = query.startingAt;
            this.upUntil = query.upUntil;
            this.contextLines = query.contextLines;
            this.limit = query.limit;
            this.sort = query.sort;
            this.offset = query.offset;
            this.skip = query.skip;
        }

        private ChatQuery build() {
            return new ChatQuery(substring, caseSensitive, regex, version, startingAt, upUntil, contextLines,
                limit, sort, offset, skip);
        }
    }
}
