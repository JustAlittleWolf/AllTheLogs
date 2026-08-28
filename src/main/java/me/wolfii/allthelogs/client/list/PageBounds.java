package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.MatchSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Decides what lies beyond the page that just arrived: whether more matches exist before or after it, whether
 * it fills the viewport, and which cursor the next page should start from.
 * <p>
 * The store pages by timestamp cursor rather than by row offset, because rows shift as logs are imported. That
 * makes "is there more?" a comparison against the unpaged {@link MatchSummary} instead of a row count.
 */
public final class PageBounds {
    private PageBounds() {
    }

    /**
     * Cursor that starts a page at {@code time} itself. Cursors are exclusive, so it has to be nudged one tick
     * past {@code time} against the sort direction.
     */
    public static LocalDateTime exclusiveOffset(LocalDateTime time, ChatQuery.Sort sort) {
        if (sort == ChatQuery.Sort.DESCENDING) {
            return time.plusNanos(1);
        }
        return time.minusNanos(1);
    }

    /**
     * Continues a paged query from {@code cursor} without dropping other matches that share that
     * second. The store cursor is exclusive on timestamp only, so the edge second is included with
     * {@link #exclusiveOffset} and already-buffered hits at that time are skipped.
     */
    public static ChatQuery continueFrom(ChatQuery page, LocalDateTime cursor, int alreadyAtCursor) {
        return page.withOffset(exclusiveOffset(cursor, page.sort()))
            .withSkip(Math.max(0, alreadyAtCursor));
    }

    /**
     * Whether matches exist before this page, judged against the whole matched range.
     */
    public static boolean hasBefore(ChatQuery.Sort sort, List<DisplayRow> rows, MatchSummary summary) {
        LocalDateTime first = DisplayRows.firstMatchTime(rows);
        if (first == null || summary == null) return false;
        if (sort == ChatQuery.Sort.ASCENDING) {
            return summary.oldest() != null && summary.oldest().isBefore(first);
        }
        return summary.newest() != null && summary.newest().isAfter(first);
    }

    /**
     * Whether matches exist after this page. A page that hit its own limit always has more.
     */
    public static boolean hasAfter(ChatQuery.Sort sort, boolean full, List<DisplayRow> rows, MatchSummary summary) {
        if (full) return true;
        LocalDateTime last = DisplayRows.lastMatchTime(rows);
        if (last == null || summary == null) return false;
        if (sort == ChatQuery.Sort.ASCENDING) {
            return summary.newest() != null && summary.newest().isAfter(last);
        }
        return summary.oldest() != null && summary.oldest().isBefore(last);
    }

    /**
     * Whether the page returned as many matches as it asked for, so more are waiting.
     */
    public static boolean isFull(List<DisplayRow> rows, long limit) {
        return limit > 0 && DisplayRows.matchCount(rows) >= limit;
    }

    /**
     * A jump that lands on the newest (or oldest) matches can return too few rows to fill the list, because
     * {@link #exclusiveOffset} starts the page at that timestamp and there is nothing beyond it. Older (or
     * newer) rows have to be fetched first in that case.
     */
    public static boolean needsMoreToFill(List<DisplayRow> rows, int contextLines, int viewHeight,
                                          boolean hasBefore) {
        if (!hasBefore || rows == null || rows.isEmpty() || viewHeight <= 0) return false;
        return MessageListLayout.of(rows, contextLines).contentHeight() < viewHeight;
    }

    /**
     * How many matches to fetch to fill a viewport of {@code viewHeight}, never fewer than a screenful of
     * single-line rows plus a margin.
     */
    public static int extraFillLimit(int viewHeight, long pageLimit) {
        int needed = Math.max(8, viewHeight / MessageListLayout.ROW_HEIGHT + 8);
        if (pageLimit < 0) return needed;
        return (int) Math.max(needed, Math.min(pageLimit, Integer.MAX_VALUE));
    }
}
