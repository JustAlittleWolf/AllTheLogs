package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One row in the log browser: a stored chat line, whether it is a search hit, and where the query matched
 * inside its display text.
 *
 * @param match            whether this row is a search hit rather than a context line around one
 * @param highlights       ranges of {@code message} the query matched; empty on context lines
 * @param message          the text as drawn, which trims each line and turns literal {@code \n} into a break
 * @param visualFormatting packed formatting remapped onto {@code message}, or {@code null} when unstyled
 */
public record DisplayRow(
    ChatEntry entry,
    boolean match,
    List<HighlightSpan> highlights,
    String message,
    long[] visualFormatting,
    boolean expandUp,
    boolean expandDown
) {
    public DisplayRow {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(highlights, "highlights");
        Objects.requireNonNull(message, "message");
        highlights = List.copyOf(highlights);
    }

    public DisplayRow(ChatEntry entry, boolean match, List<HighlightSpan> highlights) {
        this(entry, match, highlights, VisualMessage.prepare(entry));
    }

    private DisplayRow(ChatEntry entry, boolean match, List<HighlightSpan> highlights,
                       VisualMessage.Prepared prepared) {
        this(entry, match, highlights, prepared.text(), prepared.formatting(), false, false);
    }

    /**
     * Marks query results as search hits or context lines, and highlights where the query matched.
     * <p>
     * The store already applied the filter, but a page also carries the context lines around each hit, so the
     * two have to be told apart again here.
     */
    public static List<DisplayRow> from(List<ChatEntry> entries, SearchFilter filter) {
        if (entries.isEmpty()) return List.of();
        if (!filter.hasText()) {
            return entries.stream().map(entry -> new DisplayRow(entry, true, List.of())).toList();
        }
        Predicate<String> matches = filter.messagePredicate();
        return entries.stream().map(entry -> {
            VisualMessage.Prepared prepared = VisualMessage.prepare(entry);
            if (!matches.test(entry.message())) {
                return new DisplayRow(entry, false, List.of(), prepared);
            }
            return new DisplayRow(entry, true, MatchSpans.spans(prepared.text(), filter), prepared);
        }).toList();
    }

    /**
     * List-direction expand carets for this row's cluster edge. {@code expandUp} loads more toward the top
     * of the list; {@code expandDown} toward the bottom.
     */
    public DisplayRow withExpand(boolean expandUp, boolean expandDown) {
        if (this.expandUp == expandUp && this.expandDown == expandDown) return this;
        return new DisplayRow(entry, match, highlights, message, visualFormatting, expandUp, expandDown);
    }

    public ChatLog chatLog() {
        return entry.chatLog();
    }

    /**
     * Whether {@code other} is another line from the same imported file or session, ignoring metadata
     * that can change while a session is live (such as {@link ChatLog#endTime()}).
     */
    public boolean sameLog(DisplayRow other) {
        return other != null && chatLog().source().equals(other.chatLog().source());
    }

    public int lineIndex() {
        return entry.lineIndex();
    }

    public RowKey key() {
        return new RowKey(chatLog().source(), lineIndex());
    }

    /**
     * Identifies a row across page reloads, so scroll anchors and selections survive them.
     * Keyed by source and line, not the whole {@link ChatLog}, because session capture updates
     * {@code endTime} without changing the identity of earlier lines.
     */
    public record RowKey(LogSource source, int lineIndex) {
        public RowKey {
            Objects.requireNonNull(source, "source");
        }
    }
}
