package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.query.Re2Regex;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * What the browser's search bar and filter overlay currently ask for: text, regex, dates, sort, and paging.
 * <p>
 * The same filter drives two things. {@link #toQuery()} is the store query that fetches a page, and
 * {@link #messagePredicate()} re-checks the rows that come back so hits can be told apart from the context
 * lines around them.
 *
 * @param text         the search text, empty for no text filter
 * @param regex        whether {@code text} is a regular expression rather than a literal substring
 * @param limit        matches per page; negative means no cap
 * @param offset       exclusive timestamp cursor the current page starts after, or {@code null} for the first page
 * @param versions        Minecraft versions to keep, with {@link ChatQuery#NULL} for logs without one, or
 *                        {@code null} for all of them; empty keeps nothing
 * @param serversOrWorlds exact servers or worlds to keep, with {@link ChatQuery#NULL} for messages without one,
 *                        or {@code null} for all of them; empty keeps nothing
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
    Set<String> versions,
    Set<String> serversOrWorlds
) {
    public static final int MAX_CONTEXT_LINES = 1000;
    public static final int DEFAULT_LIMIT = 100;
    public static final int DEFAULT_CONTEXT_LINES = 3;

    public SearchFilter {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sort, "sort");
        if (contextLines < 0) throw new IllegalArgumentException("contextLines must not be negative");
        if (contextLines > MAX_CONTEXT_LINES) {
            throw new IllegalArgumentException("contextLines must be at most " + MAX_CONTEXT_LINES);
        }
        if (limit == 0) throw new IllegalArgumentException("limit must not be zero");
        versions = versions == null ? null : Set.copyOf(versions);
        serversOrWorlds = serversOrWorlds == null ? null : Set.copyOf(serversOrWorlds);
    }

    public static SearchFilter defaults() {
        return new SearchFilter("", false, false, DEFAULT_CONTEXT_LINES, DEFAULT_LIMIT, ChatQuery.Sort.ASCENDING,
            null, null, null, null, null);
    }

    /**
     * Compiles a search regex for Java-side matching, or empty when the pattern is malformed.
     */
    public static Optional<Pattern> compiledRegex(String regex, boolean caseSensitive) {
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            return Optional.of(Pattern.compile(regex, flags));
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * The same regex for DuckDB's RE2, which has no case-insensitive flag outside the pattern itself.
     */
    static String regexPattern(String regex, boolean caseSensitive) {
        if (caseSensitive || hasInlineCaseFlag(regex)) return regex;
        return "(?i)" + regex;
    }

    private static boolean hasInlineCaseFlag(String regex) {
        return regex.startsWith("(?i)") || regex.startsWith("(?-i)") || regex.startsWith("(?iu)");
    }

    public SearchFilter withText(String text) {
        return with(draft -> draft.text = text);
    }

    public SearchFilter withRegex(boolean regex) {
        return with(draft -> draft.regex = regex);
    }

    public SearchFilter withCaseSensitive(boolean caseSensitive) {
        return with(draft -> draft.caseSensitive = caseSensitive);
    }

    /**
     * Inclusive calendar-day bounds: midnight of {@code date} through exclusive midnight of the next day.
     */
    public SearchFilter withDay(LocalDate date) {
        if (date == null) return withStartingAt(null).withUpUntil(null);
        return withStartingAt(date.atStartOfDay()).withUpUntil(date.plusDays(1).atStartOfDay());
    }

    public SearchFilter withContextLines(int contextLines) {
        return with(draft -> draft.contextLines = contextLines);
    }

    public SearchFilter withLimit(long limit) {
        return with(draft -> draft.limit = limit);
    }

    public SearchFilter withSort(ChatQuery.Sort sort) {
        return with(draft -> draft.sort = sort);
    }

    public SearchFilter withStartingAt(LocalDateTime startingAt) {
        return with(draft -> draft.startingAt = startingAt);
    }

    public SearchFilter withUpUntil(LocalDateTime upUntil) {
        return with(draft -> draft.upUntil = upUntil);
    }

    public SearchFilter withOffset(LocalDateTime offset) {
        return with(draft -> draft.offset = offset);
    }

    /**
     * Only {@code version}, or every version when it is null or blank.
     */
    public SearchFilter withVersion(String version) {
        return withVersions(version == null || version.isBlank() ? null : Set.of(version));
    }

    /**
     * Only {@code serverOrWorld}, matched exactly, or every place when it is null or blank.
     */
    public SearchFilter withServerOrWorld(String serverOrWorld) {
        return withServersOrWorlds(serverOrWorld == null || serverOrWorld.isBlank() ? null : Set.of(serverOrWorld));
    }

    public SearchFilter withVersions(Set<String> versions) {
        return with(draft -> draft.versions = versions);
    }

    public SearchFilter withServersOrWorlds(Set<String> serversOrWorlds) {
        return with(draft -> draft.serversOrWorlds = serversOrWorlds);
    }

    public SearchFilter withoutOffset() {
        return withOffset(null);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    /**
     * Whether this filter can be sent to the store. Incomplete or RE2-incompatible regex is rejected
     * so DuckDB is not queried.
     */
    public boolean canQuery() {
        return !invalidRegex();
    }

    /**
     * Regex mode with a pattern that Java cannot compile, or that DuckDB's RE2 engine cannot run
     * (lookarounds, backreferences, possessive quantifiers). Incomplete patterns while typing count too.
     */
    public boolean invalidRegex() {
        return regexIssue() != null;
    }

    /**
     * Why this regex cannot be searched, or {@code null} when regex is off or the pattern is usable.
     * Syntax errors name the problem and the character. Constructs RE2 rejects name the construct.
     */
    public String regexIssue() {
        synchronized (REGEX_ISSUE) {
            if (REGEX_ISSUE.filter == this) return REGEX_ISSUE.issue;
            String issue = computeRegexIssue();
            REGEX_ISSUE.filter = this;
            REGEX_ISSUE.issue = issue;
            return issue;
        }
    }

    private String computeRegexIssue() {
        if (!regex || !hasText()) return null;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            Pattern.compile(text, flags);
        } catch (PatternSyntaxException error) {
            return syntaxIssue(error);
        }
        String feature = Re2Regex.unsupportedConstruct(text);
        if (feature == null) return null;
        return unsupportedIssue(feature);
    }

    private static String syntaxIssue(PatternSyntaxException error) {
        String description = error.getDescription();
        if (description == null || description.isBlank()) description = "Invalid pattern";
        int index = error.getIndex();
        String pattern = error.getPattern();
        if (index < 0 || pattern == null) return description;
        if (index >= pattern.length()) return description + " at the end";
        return description + " at character " + (index + 1);
    }

    /**
     * {@code feature} is the short name from {@link Re2Regex#unsupportedConstruct(String)}, such as
     * {@code negative lookahead}.
     */
    private static String unsupportedIssue(String feature) {
        String name = feature.isBlank() ? "This construct" : feature;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " is not supported by RE2";
    }

    public boolean hasVersion() {
        return versions != null;
    }

    public boolean hasServerOrWorld() {
        return serversOrWorlds != null;
    }

    /**
     * Whether the user has narrowed the result set. Sort, paging, and context lines do not count.
     */
    public boolean isNarrowed() {
        return hasText() || hasVersion() || hasServerOrWorld() || startingAt != null || upUntil != null;
    }

    /**
     * Context sent to the store. Unfiltered pages return every matching line, so they do not fetch
     * extra context. Text search fetches one hidden extra line around each hit so cluster edges know
     * whether they can still expand.
     */
    int queryContextLines() {
        if (!hasText()) return 0;
        return contextLines + 1;
    }

    /**
     * Store query for this filter. Empty text means every entry; regex uses DuckDB RE2 with an inline
     * {@code (?i)} flag when the search is case insensitive. Lookarounds and other Java-only constructs are
     * rejected by {@link #canQuery()} so they never reach DuckDB.
     */
    public ChatQuery toQuery() {
        return toStoreQuery(queryContextLines(), limit, offset);
    }

    /**
     * Same as {@link #toQuery()} but without context lines, a page limit, or an offset, for
     * {@link me.wolfii.allthelogs.api.LogDatabase#summarizeMatches(me.wolfii.allthelogs.api.ChatQuery)}.
     */
    public ChatQuery toSummaryQuery() {
        return toStoreQuery(0, -1, null);
    }

    /**
     * Date, version, and server or world only. Expand and context walks use this so the search term
     * does not hide neighbouring chat that still passes the filter bar.
     */
    public ChatQuery toFilterBarQuery() {
        return toStoreQuery(0, -1, null, false);
    }

    private ChatQuery toStoreQuery(int context, long pageLimit, LocalDateTime pageOffset) {
        return toStoreQuery(context, pageLimit, pageOffset, true);
    }

    private ChatQuery toStoreQuery(int context, long pageLimit, LocalDateTime pageOffset, boolean includeText) {
        ChatQuery query = ChatQuery.all()
            .withContextLines(context)
            .withLimit(pageLimit)
            .withSort(sort);
        if (startingAt != null) query = query.startingAt(startingAt);
        if (upUntil != null) query = query.upUntil(upUntil);
        if (pageOffset != null) query = query.withOffset(pageOffset);
        if (hasVersion()) query = query.withVersions(versions);
        if (hasServerOrWorld()) query = query.withServersOrWorlds(serversOrWorlds);
        if (!includeText || !hasText()) return query;
        if (regex) return query.withRegex(regexPattern(text, caseSensitive));
        if (caseSensitive) return query.withSubstringCaseSensitive(text);
        return query.withSubstring(text);
    }

    /**
     * Surrounding chat may miss the search text, but it must still sit in the date window and on a
     * matching server or world. Expand uses this so a caret cannot load another place.
     */
    public boolean allowsContext(me.wolfii.allthelogs.api.ChatEntry entry) {
        if (entry == null) return false;
        if (startingAt != null && entry.timestamp().isBefore(startingAt)) return false;
        if (upUntil != null && !entry.timestamp().isBefore(upUntil)) return false;
        if (!hasServerOrWorld()) return true;
        String place = entry.serverOrWorld();
        return serversOrWorlds.contains(place == null || place.isEmpty() ? ChatQuery.NULL : place);
    }

    /**
     * The search text as a Java pattern for cutting it out of messages: the regex itself, or the literal
     * text. Honours case sensitivity. Empty without text or with an invalid regex.
     */
    public Optional<Pattern> removalPattern() {
        if (!hasText() || invalidRegex()) return Optional.empty();
        if (regex) return compiledRegex(text, caseSensitive);
        return compiledRegex(Pattern.quote(text), caseSensitive);
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

    /**
     * Copies this filter with {@code change} applied, so each {@code with*} method only has to name the field
     * it actually touches.
     */
    private SearchFilter with(Consumer<Draft> change) {
        Draft draft = new Draft(this);
        change.accept(draft);
        return draft.build();
    }

    /**
     * Last pattern checked by {@link #regexIssue()}. The search field asks on every frame, and a long
     * pattern is expensive to compile, so a repeat on the same filter reuses the answer.
     */
    private static final class RegexIssueCache {
        private SearchFilter filter;
        private String issue;
    }

    private static final RegexIssueCache REGEX_ISSUE = new RegexIssueCache();

    /**
     * Mutable copy of a filter, used only between a {@code with*} call and the record it builds.
     */
    private static final class Draft {
        private String text;
        private boolean regex;
        private boolean caseSensitive;
        private int contextLines;
        private long limit;
        private ChatQuery.Sort sort;
        private LocalDateTime startingAt;
        private LocalDateTime upUntil;
        private LocalDateTime offset;
        private Set<String> versions;
        private Set<String> serversOrWorlds;

        private Draft(SearchFilter filter) {
            this.text = filter.text;
            this.regex = filter.regex;
            this.caseSensitive = filter.caseSensitive;
            this.contextLines = filter.contextLines;
            this.limit = filter.limit;
            this.sort = filter.sort;
            this.startingAt = filter.startingAt;
            this.upUntil = filter.upUntil;
            this.offset = filter.offset;
            this.versions = filter.versions;
            this.serversOrWorlds = filter.serversOrWorlds;
        }

        private SearchFilter build() {
            return new SearchFilter(text, regex, caseSensitive, contextLines, limit, sort, startingAt,
                upUntil, offset, versions, serversOrWorlds);
        }
    }
}
