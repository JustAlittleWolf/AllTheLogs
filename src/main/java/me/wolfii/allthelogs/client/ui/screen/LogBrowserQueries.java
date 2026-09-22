package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.list.*;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.timeline.ScrubJump;
import me.wolfii.allthelogs.client.timeline.TimelineEdge;
import me.wolfii.allthelogs.client.ui.text.StoreSummary;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.MatchSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    private SearchFilter filter;
    private MessageTimeline list;
    private ButtonComponent info;
    private List<String> versions = List.of();
    private MatchSummary matchSummary = MatchSummary.empty();
    private boolean reloadPending = true;
    private boolean replaceOnJumpFailure;
    private ListSnapshot snapshot = ListSnapshot.EMPTY;
    private static ListSnapshot sessionSnapshot = ListSnapshot.EMPTY;
    private static MatchSummary sessionMatchSummary = MatchSummary.empty();

    LogBrowserQueries() {
        this(SearchFilter.defaults());
    }

    LogBrowserQueries(SearchFilter initial) {
        this.filter = initial == null ? SearchFilter.defaults() : initial;
    }

    /**
     * Restores the last closed viewport for this Minecraft run.
     */
    void restoreSessionLocation() {
        if (sessionSnapshot.isEmpty()) return;
        snapshot = sessionSnapshot;
        matchSummary = sessionMatchSummary;
        reloadPending = false;
    }

    /**
     * Keeps the current viewport for the next time the browser opens during this run.
     */
    void rememberSessionLocation() {
        takeSnapshot();
        sessionSnapshot = snapshot;
        sessionMatchSummary = matchSummary;
    }

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
        refreshStats();
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

    static boolean keepViewport(LocalDateTime visibleTime, boolean hasRows) {
        return visibleTime != null && hasRows;
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
     * the user sees: the list starts scrolled to the bottom. If the list already has a location, clearing the
     * query or searching again keeps that place: a new term jumps to the closest match in either direction
     * of the message two-thirds down the viewport, and that message stays at the same screen position.
     */
    void reload() {
        if (list == null) return;
        reloadPending = false;
        list.beginNewSearchCount();
        refreshStats();
        if (!filter.canQuery()) {
            generation.incrementAndGet();
            list.setLoading(false);
            return;
        }
        LocalDateTime stayAt = list.stayVisibleTime();
        if (keepViewport(stayAt, !list.window().rows().isEmpty())) {
            long startedAt = System.nanoTime();
            replaceOnJumpFailure = true;
            jumpTo(new ScrubJump(stayAt, -1, Double.NaN), false);
            loadMatchSummary(generation.get(), filter.withoutOffset(), startedAt);
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
                logQueryFailure("AllTheLogs query failed", error);
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
            list.offerPageMatchCount(DisplayRows.matchCount(rows), elapsedMs(startedAt), filter.isNarrowed());
            takeSnapshot();
            loadMatchSummary(gen, query, startedAt);
        });
    }

    void refreshStats() {
        if (info == null) return;
        onClient(AllTheLogsClient.worker().browserMetadata(), (metadata, error) -> {
            if (info == null) return;
            if (error != null || metadata == null) {
                info.tooltip(List.of(Component.translatable("allthelogs.meta.unavailable")));
                return;
            }
            info.tooltip(StoreSummary.tooltip(metadata));
            versions = metadata.minecraftVersions();
            if (list != null && !filter.isNarrowed()) {
                list.setTotalMatchCount(metadata.chatEntryCount());
                takeSnapshot();
            }
        });
    }

    /**
     * Timeline totals after the page is on screen, so a slower summarize cannot overwrite
     * the list before rows arrive, or land on a newer search.
     */
    private void loadMatchSummary(int gen, SearchFilter page, long startedAt) {
        onClient(AllTheLogsClient.worker().summarizeMatches(page.toSummaryQuery()), (summary, error) -> {
            if (gen != generation.get() || list == null) return;
            if (error != null) {
                logQueryFailure("AllTheLogs match summary failed", error);
                return;
            }
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
        DisplayRow cursor = towardStart ? DisplayRows.firstMatch(list.window().rows())
            : DisplayRows.lastMatch(list.window().rows());
        if (cursor == null) return;
        list.setLoading(true);
        List<DisplayRow> buffered = list.window().rows();
        long pageLimit = list.autoScrolling() ? MessageTimeline.AUTO_SCROLL_PAGE_SIZE : filter.limit();
        SearchFilter page = towardStart
            ? filter.withoutOffset().withSort(filter.sort().opposite()).withLimit(pageLimit)
            : filter.withoutOffset().withLimit(pageLimit);
        ChatQuery query = PageBounds.continueFrom(page.toQuery(), cursor);
        DisplayRow.RowKey anchor = list.visibleAnchor();
        int firstVisible = list.firstVisibleIndex();
        int lastVisible = list.lastVisibleIndex();
        Set<DisplayRow.RowKey> bufferedKeys = DisplayRows.keysOf(buffered);
        int gen = generation.get();
        onClient(AllTheLogsClient.worker().findEntries(query), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                logQueryFailure("AllTheLogs page query failed", error);
                return;
            }
            List<DisplayRow> incoming = displaySearchRows(entries);
            if (towardStart) incoming = DisplayRows.reversed(incoming);
            int added = DisplayRows.countNewKeys(incoming, bufferedKeys);
            boolean more = added > 0 && PageBounds.isFull(incoming, pageLimit);

            List<DisplayRow> current = list.window().rows();
            List<DisplayRow> merged = towardStart
                ? DisplayRows.mergeUnique(incoming, current)
                : DisplayRows.mergeUnique(current, incoming);
            int shift = towardStart ? added : 0;
            int keep = (int) Math.max(1, Math.min(pageLimit, Integer.MAX_VALUE));
            List<DisplayRow> trimmed = DisplayRows.trimToMatchLimit(
                merged, keep, firstVisible + shift, lastVisible + shift);
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
    private void expandAround(DisplayRow row, TimelineEdge side, int extra) {
        if (list == null) return;
        boolean older = MessageListLayout.expandOlderMessages(
            side == TimelineEdge.BEFORE, filter.sort() == ChatQuery.Sort.ASCENDING);
        DisplayRow.RowKey anchor = row.key();
        boolean oldestFirst = filter.sort() == ChatQuery.Sort.ASCENDING;
        onClient(AllTheLogsClient.worker().matchingContextToward(
                row.chatLog(), row.lineIndex(), older, extra + 1,
                filter.toFilterBarQuery(), row.entry().timestamp().toLocalDate()),
            (entries, error) -> {
                if (error != null || list == null) {
                    if (error != null) logQueryFailure("AllTheLogs expand query failed", error);
                    return;
                }
                List<ChatEntry> withAnchor = new ArrayList<>(entries.size() + 1);
                withAnchor.add(row.entry());
                withAnchor.addAll(entries);
                List<DisplayRow> fetched = ContextPeeks.forExpand(displayRows(withAnchor), row, older, extra,
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
        LocalDateTime requestedTime = jump == null ? null : jump.time();
        LocalDateTime target = replaceOnJumpFailure ? requestedTime : clampToMatchedRange(requestedTime);
        if (target == null && (jump == null || jump.skip() < 0)) {
            if (preview) {
                if (list != null) list.scrubQueryFinished();
            } else {
                list.finishScrub();
            }
            return;
        }
        if (replaceOnJumpFailure && (jump == null || jump.skip() < 0)) {
            jumpToClosest(target, preview);
            return;
        }
        ChatQuery requested = jumpQuery(jump, target, preview);
        int gen = generation.incrementAndGet();
        int epoch = list.scrubEpoch();
        if (!preview) list.setLoading(true);
        onClient(AllTheLogsClient.worker().findEntries(requested), (entries, error) -> {
            applyJumpEntries(jump, target, preview, gen, epoch, requested, entries, error);
        });
    }

    /**
     * Fetches matches on both sides of {@code target} and keeps them together so clearing a search can
     * still scroll older messages. A one-sided exclusive offset from the latest line would otherwise
     * miss every earlier hit, and picking only the closer side locked upward scrolling after search.
     */
    private void jumpToClosest(LocalDateTime target, boolean preview) {
        SearchFilter page = filter.withoutOffset();
        ChatQuery later = previewLimit(page.withSort(ChatQuery.Sort.ASCENDING)
            .withOffset(PageBounds.exclusiveOffset(target, ChatQuery.Sort.ASCENDING)).toQuery(), preview);
        ChatQuery earlier = previewLimit(page.withSort(ChatQuery.Sort.DESCENDING)
            .withOffset(PageBounds.exclusiveOffset(target, ChatQuery.Sort.DESCENDING)).toQuery(), preview);
        int gen = generation.incrementAndGet();
        int epoch = list.scrubEpoch();
        if (!preview) list.setLoading(true);
        CompletableFuture<List<ChatEntry>> laterFuture = AllTheLogsClient.worker().findEntries(later);
        CompletableFuture<List<ChatEntry>> earlierFuture = AllTheLogsClient.worker().findEntries(earlier);
        onClient(laterFuture, (laterEntries, laterError) ->
            onClient(earlierFuture, (earlierEntries, earlierError) -> {
                if (gen != generation.get()) {
                    finishPreview(preview, epoch);
                    return;
                }
                List<ChatEntry> laterRows = laterError == null ? laterEntries : List.of();
                List<ChatEntry> earlierRows = earlierError == null ? earlierEntries : List.of();
                List<DisplayRow> laterDisplay = displaySearchRows(
                    orderedForFilter(laterRows, ChatQuery.Sort.ASCENDING));
                List<DisplayRow> earlierDisplay = displaySearchRows(
                    orderedForFilter(earlierRows, ChatQuery.Sort.DESCENDING));
                List<DisplayRow> merged = mergeClosestSides(earlierDisplay, laterDisplay, filter.sort());
                if (merged.isEmpty()) {
                    Throwable error = laterError != null ? laterError : earlierError;
                    applyJumpEntries(null, target, preview, gen, epoch, later, List.of(), error);
                    return;
                }
                boolean earlierFull = PageBounds.isFull(earlierDisplay, earlier.limit());
                boolean laterFull = PageBounds.isFull(laterDisplay, later.limit());
                boolean hasBefore = hasMoreBefore(earlierFull, filter.sort(), merged, matchSummary);
                boolean hasAfter = hasMoreAfter(laterFull, filter.sort(), merged, matchSummary);
                if (PageBounds.needsMoreToFill(merged, filter.contextLines(), list.viewHeight(), hasBefore)) {
                    if (preview) {
                        applyJump(target, true, merged, hasBefore, hasAfter, Double.NaN, epoch, false);
                    }
                    fillJumpViewport(gen, epoch, target, preview, merged, hasAfter, later.limit(), Double.NaN);
                    return;
                }
                applyJump(target, preview, merged, hasBefore, hasAfter, Double.NaN, epoch, true);
            }));
    }

    static List<DisplayRow> mergeClosestSides(List<DisplayRow> earlier, List<DisplayRow> later,
                                              ChatQuery.Sort sort) {
        return DisplayRows.mergeSorted(earlier == null ? List.of() : earlier, later == null ? List.of() : later,
            sort);
    }

    static boolean hasMoreBefore(boolean earlierPageFull, ChatQuery.Sort sort, List<DisplayRow> rows,
                                 MatchSummary summary) {
        return earlierPageFull || PageBounds.hasBefore(sort, rows, summary);
    }

    static boolean hasMoreAfter(boolean laterPageFull, ChatQuery.Sort sort, List<DisplayRow> rows,
                                MatchSummary summary) {
        return laterPageFull || PageBounds.hasAfter(sort, laterPageFull, rows, summary);
    }

    private void applyJumpEntries(ScrubJump jump, LocalDateTime target, boolean preview, int gen, int epoch,
                                  ChatQuery requested, List<ChatEntry> entries, Throwable error) {
        if (gen != generation.get()) {
            finishPreview(preview, epoch);
            return;
        }
        List<DisplayRow> rows = error == null && entries != null ? displaySearchRows(entries) : List.of();
        if (error != null || rows.isEmpty()) {
            if (error != null) logQueryFailure("AllTheLogs jump query failed", error);
            list.setLoading(false);
            if (replaceOnJumpFailure && !preview) {
                replaceOnJumpFailure = false;
                list.reset(List.of(), false, false);
                if (error != null) {
                    list.showOverlay(Component.translatable("allthelogs.status.error"));
                }
                takeSnapshot();
            } else if (!preview) {
                list.finishScrub();
            }
            finishPreview(preview, epoch);
            return;
        }
        boolean full = PageBounds.isFull(rows, requested.limit());
        double progress = jump == null ? Double.NaN : jump.progress();
        long skipped = jump == null ? -1 : jump.skip();
        boolean hasBefore = PageBounds.hasBefore(filter.sort(), rows, matchSummary, skipped);
        boolean hasAfter = PageBounds.hasAfter(filter.sort(), full, rows, matchSummary);
        if (PageBounds.needsMoreToFill(rows, filter.contextLines(), list.viewHeight(), hasBefore)) {
            if (preview) {
                applyJump(target, true, rows, hasBefore, hasAfter, progress, epoch, false);
            }
            fillJumpViewport(gen, epoch, target, preview, rows, hasAfter, requested.limit(), progress);
            return;
        }
        applyJump(target, preview, rows, hasBefore, hasAfter, progress, epoch, true);
    }

    /**
     * Drops the preview slot once its page is visible, or once the query has failed. Holding the slot until
     * then stops the next thumb position from invalidating a page the list has not drawn yet.
     */
    private void finishPreview(boolean preview, int epoch) {
        if (preview && list != null) list.scrubQueryFinished(epoch);
    }

    private ChatQuery jumpQuery(ScrubJump jump, LocalDateTime target, boolean preview) {
        SearchFilter page = filter.withoutOffset();
        ChatQuery query = jump != null && jump.skip() >= 0
            ? page.toQuery().withSkip(jump.skip())
            : page.withOffset(PageBounds.exclusiveOffset(target, filter.sort())).toQuery();
        return previewLimit(query, preview);
    }

    private ChatQuery previewLimit(ChatQuery query, boolean preview) {
        if (!preview) return query;
        long cap = filter.limit() < 0
            ? MessageTimeline.SCRUB_PAGE_SIZE
            : Math.min(MessageTimeline.SCRUB_PAGE_SIZE, filter.limit());
        return query.withLimit(Math.max(8, cap));
    }

    private List<ChatEntry> orderedForFilter(List<ChatEntry> entries, ChatQuery.Sort fetched) {
        if (entries == null || entries.isEmpty() || fetched == filter.sort()) return entries;
        List<ChatEntry> reversed = new java.util.ArrayList<>(entries);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Prepends older matches to a jump that landed too close to the end of the result set to fill the list.
     */
    private void fillJumpViewport(int gen, int epoch, LocalDateTime target, boolean preview, List<DisplayRow> rows,
                                  boolean hasAfter, long pageLimit, double progress) {
        DisplayRow cursor = DisplayRows.firstMatch(rows);
        if (cursor == null) {
            applyJump(target, preview, rows, true, hasAfter, progress, epoch, true);
            return;
        }
        SearchFilter extra = filter.withoutOffset().withSort(filter.sort().opposite())
            .withLimit(PageBounds.extraFillLimit(list.viewHeight(), pageLimit));
        ChatQuery extraQuery = PageBounds.continueFrom(extra.toQuery(), cursor);
        Set<DisplayRow.RowKey> alreadyLoaded = DisplayRows.keysOf(rows);
        onClient(AllTheLogsClient.worker().findEntries(extraQuery), (entries, error) -> {
            if (gen != generation.get()) {
                finishPreview(preview, epoch);
                return;
            }
            if (error != null) {
                logQueryFailure("AllTheLogs jump fill query failed", error);
                applyJump(target, preview, rows, true, hasAfter, progress, epoch, true);
                return;
            }
            List<DisplayRow> incoming = DisplayRows.reversed(displaySearchRows(entries));
            boolean more = PageBounds.isFull(incoming, extra.limit())
                && DisplayRows.countNewKeys(incoming, alreadyLoaded) > 0;
            List<DisplayRow> merged = DisplayRows.mergeUnique(incoming, rows);
            applyJump(target, preview, merged,
                more || PageBounds.hasBefore(filter.sort(), merged, matchSummary),
                hasAfter || PageBounds.hasAfter(filter.sort(), false, merged, matchSummary),
                progress, epoch, true);
        });
    }

    private void applyJump(LocalDateTime target, boolean preview, List<DisplayRow> rows,
                           boolean hasBefore, boolean hasAfter, double progress, int epoch,
                           boolean releasePreview) {
        boolean fromSearch = replaceOnJumpFailure;
        replaceOnJumpFailure = false;
        list.setLoading(false);
        list.showAt(target, rows, hasBefore, hasAfter, progress,
            fromSearch ? MessageTimeline.VIEWPORT_STAY_FRACTION : 0);
        if (preview && releasePreview) {
            finishPreview(true, epoch);
        } else if (!preview && releasePreview) {
            list.finishScrub();
        }
        if (fromSearch) {
            list.offerPageMatchCount(DisplayRows.matchCount(rows), 0, filter.isNarrowed());
        }
        takeSnapshot();
    }

    private List<DisplayRow> displaySearchRows(List<ChatEntry> entries) {
        return ContextPeeks.forSearchPage(displayRows(entries), filter.hasText(), filter.contextLines(),
            filter.sort() == ChatQuery.Sort.ASCENDING);
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

    void takeSnapshot() {
        if (list == null) return;
        snapshot = ListSnapshot.of(list);
    }

    /**
     * Puts the failure text in the log line itself. Minecraft's log layout often omits the throwable,
     * and {@link java.util.concurrent.CompletionException} wraps the store error so only the outer
     * {@code could not run query ChatQuery[...]} would be visible.
     */
    static void logQueryFailure(String label, Throwable error) {
        Throwable failure = unwrap(error);
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            AllTheLogsClient.LOGGER.warn(label, failure);
        } else {
            AllTheLogsClient.LOGGER.warn("{}: {}", label, message, failure);
        }
    }

    static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
