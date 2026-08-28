package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.list.ContextPeeks;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.DisplayRows;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.list.PageBounds;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.timeline.ScrubJump;
import me.wolfii.allthelogs.client.timeline.TimelineEdge;
import me.wolfii.allthelogs.client.ui.text.StoreSummary;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.MatchSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Runs the browser's log-store queries and applies the pages to {@link MessageTimeline}.
 * <p>
 * Every query is answered on the store's worker thread and applied back on the client thread, so results can
 * arrive after the search that asked for them is gone. A generation counter guards that: it is bumped whenever
 * the user changes something, and a result whose generation is stale is dropped. The widget is rebuilt every
 * time the screen is resized or reopened, so the last applied page is also kept here as a
 * {@link ListSnapshot} and re-applied on {@link #attach}.
 * <p>
 * Which rows exist beyond a page is decided by {@link PageBounds}; stitching pages together is
 * {@link DisplayRows}.
 */
final class LogBrowserQueries {
    private final AtomicInteger generation = new AtomicInteger();
    private SearchFilter filter = SearchFilter.defaults();
    private MessageTimeline list;
    private ButtonComponent info;
    private List<String> versions = List.of();
    private MatchSummary matchSummary = MatchSummary.empty();
    private boolean reloadPending = true;
    private ListSnapshot snapshot = ListSnapshot.EMPTY;

    private static long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static <T> void onClient(CompletableFuture<T> future, BiConsumer<T, Throwable> handler) {
        future.whenComplete((value, error) -> Minecraft.getInstance().execute(() -> handler.accept(value, error)));
    }

    SearchFilter filter() {
        return filter;
    }

    List<String> versions() {
        return versions;
    }

    boolean consumeReload() {
        if (!reloadPending) return false;
        reloadPending = false;
        return true;
    }

    void markReload() {
        reloadPending = true;
    }

    /**
     * Binds a freshly built widget, restoring the page that was on screen before it was rebuilt.
     */
    void attach(MessageTimeline list, ButtonComponent info) {
        takeSnapshot();
        this.list = list;
        this.info = info;
        list.setContextLines(filter.contextLines());
        list.onApproachEdge(this::loadMore);
        list.onJump(this::jumpTo);
        list.onExpand(this::expandAround);
        list.onScrubBegin(this::beginScrub);
        if (reloadPending || snapshot.isEmpty()) return;
        list.restore(snapshot.rows(), snapshot.hasBefore(), snapshot.hasAfter(), snapshot.scrollY());
        list.setMatchSummary(matchSummary);
        list.showMatchCount(snapshot.matchCount(), snapshot.elapsedMs(), filter.isNarrowed());
        if (snapshot.exactMatchCount()) {
            list.setTotalMatchCount(snapshot.matchCount());
        }
    }

    void updateFilter(SearchFilter next) {
        filter = next.withoutOffset();
        if (list != null) list.setContextLines(filter.contextLines());
    }

    void setFilter(SearchFilter next) {
        updateFilter(next);
        reload();
    }

    int bumpGeneration() {
        return generation.incrementAndGet();
    }

    int currentGeneration() {
        return generation.get();
    }

    /**
     * Runs the current filter from scratch.
     * <p>
     * An oldest-first search still fetches its newest page first, then reverses it, because that is the page
     * the user sees: the list starts scrolled to the bottom.
     */
    void reload() {
        if (list == null) return;
        reloadPending = false;
        if (!filter.canQuery()) {
            generation.incrementAndGet();
            list.setLoading(false);
            return;
        }
        int gen = generation.incrementAndGet();
        list.setLoading(true);
        boolean chronological = filter.sort() == ChatQuery.Sort.ASCENDING;
        SearchFilter query = chronological
            ? filter.withoutOffset().withSort(ChatQuery.Sort.DESCENDING)
            : filter.withoutOffset();
        long startedAt = System.nanoTime();
        onClient(AllTheLogsClient.worker().findEntries(query.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("AllTheLogs query failed", error);
                list.reset(List.of(), false, false);
                list.showOverlay(Component.translatable("allthelogs.status.error"));
                takeSnapshot();
                return;
            }
            List<DisplayRow> rows = displaySearchRows(entries);
            if (chronological) rows = DisplayRows.reversed(rows);
            boolean full = PageBounds.isFull(rows, query.limit());
            list.reset(rows, chronological && full, !chronological && full);
            if (chronological) list.scrollToEnd();
            list.showMatchCount(DisplayRows.matchCount(rows), elapsedMs(startedAt), filter.isNarrowed());
            takeSnapshot();
            loadMatchSummary(gen, query, startedAt);
        });
        refreshStats();
    }

    void refreshStats() {
        if (info == null) return;
        onClient(AllTheLogsClient.worker().metadata(), (metadata, error) -> {
            if (info == null) return;
            if (error != null || metadata == null) {
                info.tooltip(List.of(Component.translatable("allthelogs.meta.unavailable")));
                return;
            }
            info.tooltip(StoreSummary.tooltip(metadata));
            versions = metadata.minecraftVersions();
        });
    }

    /**
     * Timeline totals after the page is on screen, so a slower summarize cannot overwrite
     * the list before rows arrive, or land on a newer search.
     */
    private void loadMatchSummary(int gen, SearchFilter page, long startedAt) {
        onClient(AllTheLogsClient.worker().summarizeMatches(page.toSummaryQuery()), (summary, error) -> {
            if (gen != generation.get() || error != null || list == null) return;
            matchSummary = summary == null ? MatchSummary.empty() : summary;
            list.setMatchSummary(matchSummary);
            list.setTotalMatchCount(matchSummary.matches(), elapsedMs(startedAt));
            takeSnapshot();
        });
    }

    /**
     * Extends the buffer at whichever end the viewport has reached, then trims the far end back to the page
     * limit so the buffer cannot grow without bound while scrolling.
     */
    private void loadMore(TimelineEdge edge) {
        if (!filter.canQuery() || list.loading() || list.window().rows().isEmpty()) return;
        boolean towardStart = edge == TimelineEdge.BEFORE;
        LocalDateTime cursor = towardStart ? list.window().firstMatchTime() : list.window().lastMatchTime();
        if (cursor == null) return;
        list.setLoading(true);
        SearchFilter page = towardStart
            ? filter.withOffset(cursor).withSort(filter.sort().opposite())
            : filter.withOffset(cursor);
        DisplayRow.RowKey anchor = list.visibleAnchor();
        int firstVisible = list.firstVisibleIndex();
        int lastVisible = list.lastVisibleIndex();
        Set<DisplayRow.RowKey> bufferedKeys = DisplayRows.keysOf(list.window().rows());
        int gen = generation.get();
        onClient(AllTheLogsClient.worker().findEntries(page.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("AllTheLogs page query failed", error);
                return;
            }
            List<DisplayRow> incoming = displaySearchRows(entries);
            if (towardStart) incoming = DisplayRows.reversed(incoming);
            int added = DisplayRows.countNewKeys(incoming, bufferedKeys);
            boolean more = added > 0 && PageBounds.isFull(incoming, filter.limit());

            List<DisplayRow> buffered = list.window().rows();
            List<DisplayRow> merged = towardStart
                ? DisplayRows.mergeUnique(incoming, buffered)
                : DisplayRows.mergeUnique(buffered, incoming);
            int shift = towardStart ? added : 0;
            List<DisplayRow> trimmed = DisplayRows.trimToMatchLimit(
                merged, (int) Math.max(1, filter.limit()), firstVisible + shift, lastVisible + shift);
            boolean hasBefore = (towardStart ? more : list.window().hasBefore())
                || DisplayRows.trimmedHead(merged, trimmed);
            boolean hasAfter = (towardStart ? list.window().hasAfter() : more)
                || DisplayRows.trimmedTail(merged, trimmed);
            list.applyPage(trimmed, hasBefore, hasAfter, anchor);
            takeSnapshot();
        });
    }

    /**
     * Loads more context from a separator caret, on the side that was clicked.
     */
    private void expandAround(DisplayRow row, TimelineEdge side) {
        if (list == null) return;
        int extra = MessageListLayout.extraContextLines();
        boolean older = MessageListLayout.expandOlderMessages(
            side == TimelineEdge.BEFORE, filter.sort() == ChatQuery.Sort.ASCENDING);
        int before = older ? extra + 1 : 0;
        int after = older ? 0 : extra + 1;
        DisplayRow.RowKey anchor = row.key();
        boolean oldestFirst = filter.sort() == ChatQuery.Sort.ASCENDING;
        onClient(AllTheLogsClient.worker().entriesAround(row.chatLog(), row.lineIndex(), before, after),
            (entries, error) -> {
                if (error != null || list == null) {
                    if (error != null) AllTheLogsClient.LOGGER.warn("AllTheLogs expand query failed", error);
                    return;
                }
                List<DisplayRow> fetched = ContextPeeks.forExpand(displayRows(entries), row, older, extra,
                    oldestFirst);
                List<DisplayRow> merged = ContextPeeks.mergeAfterExpand(
                    list.window().rows(), fetched, row, older, filter.sort());
                list.applyPage(merged, list.window().hasBefore(), list.window().hasAfter(), anchor);
                list.setScrollY(list.scrollY());
                takeSnapshot();
            });
    }

    /**
     * A drag on the timeline invalidates whatever page is still in flight, and stops the loading chip from
     * flickering for the duration of the drag.
     */
    private void beginScrub() {
        generation.incrementAndGet();
        list.setLoading(false);
    }

    /**
     * Loads the page a timeline drag points at. Previews, sent while the thumb is still held, fetch a smaller
     * page and leave the thumb where it is.
     */
    private void jumpTo(ScrubJump jump, boolean preview) {
        if (!filter.canQuery()) {
            if (preview) {
                if (list != null) list.scrubQueryFinished();
            } else if (list != null) {
                list.finishScrub();
            }
            return;
        }
        LocalDateTime target = clampToMatchedRange(jump == null ? null : jump.time());
        if (target == null && (jump == null || jump.skip() < 0)) {
            if (preview) {
                if (list != null) list.scrubQueryFinished();
            } else {
                list.finishScrub();
            }
            return;
        }
        ChatQuery requested = jumpQuery(jump, target, preview);
        int gen = generation.incrementAndGet();
        if (!preview) list.setLoading(true);
        onClient(AllTheLogsClient.worker().findEntries(requested), (entries, error) -> {
            if (preview && list != null) list.scrubQueryFinished();
            if (gen != generation.get()) return;
            List<DisplayRow> rows = error == null ? displaySearchRows(entries) : List.of();
            if (error != null || rows.isEmpty()) {
                list.setLoading(false);
                if (!preview) list.finishScrub();
                return;
            }
            boolean full = PageBounds.isFull(rows, requested.limit());
            double progress = jump == null ? Double.NaN : jump.progress();
            boolean hasBefore = PageBounds.hasBefore(filter.sort(), rows, matchSummary);
            boolean hasAfter = PageBounds.hasAfter(filter.sort(), full, rows, matchSummary);
            if (PageBounds.needsMoreToFill(rows, filter.contextLines(), list.viewHeight(), hasBefore)) {
                fillJumpViewport(gen, target, preview, rows, hasAfter, requested.limit(), progress);
                return;
            }
            applyJump(target, preview, rows, hasBefore, hasAfter, progress);
        });
    }

    private ChatQuery jumpQuery(ScrubJump jump, LocalDateTime target, boolean preview) {
        SearchFilter page = filter.withoutOffset();
        ChatQuery query = jump != null && jump.skip() >= 0
            ? page.toQuery().withSkip(jump.skip())
            : page.withOffset(PageBounds.exclusiveOffset(target, filter.sort())).toQuery();
        if (!preview) return query;
        long cap = filter.limit() < 0
            ? MessageTimeline.SCRUB_PAGE_SIZE
            : Math.min(MessageTimeline.SCRUB_PAGE_SIZE, filter.limit());
        return query.withLimit(Math.max(8, cap));
    }

    /**
     * Prepends older matches to a jump that landed too close to the end of the result set to fill the list.
     */
    private void fillJumpViewport(int gen, LocalDateTime target, boolean preview, List<DisplayRow> rows,
                                  boolean hasAfter, long pageLimit, double progress) {
        LocalDateTime cursor = DisplayRows.firstMatchTime(rows);
        if (cursor == null) {
            applyJump(target, preview, rows, true, hasAfter, progress);
            return;
        }
        SearchFilter extra = filter.withOffset(cursor).withSort(filter.sort().opposite())
            .withLimit(PageBounds.extraFillLimit(list.viewHeight(), pageLimit));
        Set<DisplayRow.RowKey> alreadyLoaded = DisplayRows.keysOf(rows);
        onClient(AllTheLogsClient.worker().findEntries(extra.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            if (error != null) {
                applyJump(target, preview, rows, true, hasAfter, progress);
                return;
            }
            List<DisplayRow> incoming = DisplayRows.reversed(displaySearchRows(entries));
            boolean more = PageBounds.isFull(incoming, extra.limit())
                && DisplayRows.countNewKeys(incoming, alreadyLoaded) > 0;
            List<DisplayRow> merged = DisplayRows.mergeUnique(incoming, rows);
            applyJump(target, preview, merged,
                more || PageBounds.hasBefore(filter.sort(), merged, matchSummary),
                hasAfter || PageBounds.hasAfter(filter.sort(), false, merged, matchSummary), progress);
        });
    }

    private void applyJump(LocalDateTime target, boolean preview, List<DisplayRow> rows,
                           boolean hasBefore, boolean hasAfter, double progress) {
        list.setLoading(false);
        list.showAt(target, rows, hasBefore, hasAfter, progress);
        if (!preview) list.finishScrub();
        takeSnapshot();
    }

    private List<DisplayRow> displaySearchRows(List<ChatEntry> entries) {
        List<DisplayRow> rows = displayRows(entries);
        boolean oldestFirst = filter.sort() == ChatQuery.Sort.ASCENDING;
        if (filter.hasText()) {
            return ContextPeeks.strip(rows, filter.contextLines(), true, oldestFirst);
        }
        if (filter.hasVersion() || filter.startingAt() != null || filter.upUntil() != null) {
            return ContextPeeks.markFileGaps(rows, oldestFirst);
        }
        return rows;
    }

    private List<DisplayRow> displayRows(List<ChatEntry> entries) {
        return DisplayRow.from(entries, filter);
    }

    /**
     * Keeps a drag target inside the matched range, so a drag past either end of the track still lands on a
     * real match.
     */
    private LocalDateTime clampToMatchedRange(LocalDateTime time) {
        if (time == null) return null;
        LocalDateTime oldest = matchSummary.oldest();
        LocalDateTime newest = matchSummary.newest();
        if (oldest == null || newest == null) return time;
        if (time.isBefore(oldest)) return oldest;
        if (time.isAfter(newest)) return newest;
        return time;
    }

    private void takeSnapshot() {
        if (list == null) return;
        snapshot = ListSnapshot.of(list);
    }
}
