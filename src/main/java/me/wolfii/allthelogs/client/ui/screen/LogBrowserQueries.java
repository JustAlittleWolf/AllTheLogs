package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.list.ResultWindow;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.text.StoreSummary;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import me.wolfii.allthelogs.data.ChatQuery;
import me.wolfii.allthelogs.data.MatchSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Runs log-store queries for the browser and applies the pages to {@link MessageTimeline}.
 */
final class LogBrowserQueries {
    private final AtomicInteger generation = new AtomicInteger();
    private SearchFilter filter = SearchFilter.defaults();
    private MessageTimeline list;
    private ButtonComponent info;
    private List<String> versions = List.of();
    private MatchSummary matchSummary = MatchSummary.empty();
    private boolean reloadPending = true;
    private List<DisplayRow> restoredRows = List.of();
    private boolean restoredHasBefore;
    private boolean restoredHasAfter;
    private double restoredScrollY;
    private long restoredMatchCount;
    private boolean restoredExactMatchCount;
    private long restoredElapsedMs;

    /**
     * Exclusive cursor that still includes {@code time} itself for the current sort.
     */
    static LocalDateTime exclusiveOffset(LocalDateTime time, ChatQuery.Sort sort) {
        if (sort == ChatQuery.Sort.DESCENDING) {
            return time.plusNanos(1);
        }
        return time.minusNanos(1);
    }

    static boolean pageHasBefore(ChatQuery.Sort sort, List<DisplayRow> rows, MatchSummary summary) {
        LocalDateTime first = firstMatchTime(rows);
        if (first == null || summary == null) return false;
        if (sort == ChatQuery.Sort.ASCENDING) {
            return summary.oldest() != null && summary.oldest().isBefore(first);
        }
        return summary.newest() != null && summary.newest().isAfter(first);
    }

    static boolean pageHasAfter(ChatQuery.Sort sort, boolean full, List<DisplayRow> rows, MatchSummary summary) {
        if (full) return true;
        LocalDateTime last = lastMatchTime(rows);
        if (last == null || summary == null) return false;
        if (sort == ChatQuery.Sort.ASCENDING) {
            return summary.newest() != null && summary.newest().isAfter(last);
        }
        return summary.oldest() != null && summary.oldest().isBefore(last);
    }

    static LocalDateTime firstMatchTime(List<DisplayRow> rows) {
        for (DisplayRow row : rows) {
            if (row.match()) return row.entry().timestamp();
        }
        return rows.isEmpty() ? null : rows.getFirst().entry().timestamp();
    }

    static LocalDateTime lastMatchTime(List<DisplayRow> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).match()) return rows.get(i).entry().timestamp();
        }
        return rows.isEmpty() ? null : rows.getLast().entry().timestamp();
    }

    private static Set<DisplayRow.RowKey> keysOf(List<DisplayRow> rows) {
        Set<DisplayRow.RowKey> keys = new HashSet<>(rows.size() * 2);
        for (DisplayRow row : rows) {
            keys.add(row.key());
        }
        return keys;
    }

    private static int countNewKeys(List<DisplayRow> rows, Set<DisplayRow.RowKey> existing) {
        int count = 0;
        for (DisplayRow row : rows) {
            if (!existing.contains(row.key())) count++;
        }
        return count;
    }

    private static boolean pageIsFull(List<DisplayRow> rows, SearchFilter page) {
        return ResultWindow.matchCount(rows) >= page.limit() && page.limit() > 0;
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

    void attach(MessageTimeline list, ButtonComponent info) {
        snapshotCurrentList();
        this.list = list;
        this.info = info;
        list.setContextLines(filter.contextLines());
        list.onApproachEdge(this::loadMore);
        list.onJump(this::jumpTo);
        list.onExpand(this::expandAround);
        list.onScrubBegin(this::beginScrub);
        if (!reloadPending && !restoredRows.isEmpty()) {
            list.restore(restoredRows, restoredHasBefore, restoredHasAfter, restoredScrollY);
            list.setMatchSummary(matchSummary);
            list.showMatchCount(restoredMatchCount, restoredElapsedMs);
            if (restoredExactMatchCount) {
                list.setTotalMatchCount(restoredMatchCount);
            }
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

    void reload() {
        if (list == null) return;
        reloadPending = false;
        int gen = generation.incrementAndGet();
        list.setLoading(true);
        boolean chronological = filter.sort() == ChatQuery.Sort.ASCENDING;
        SearchFilter page = filter.withoutOffset();
        if (chronological) {
            page = page.withSort(ChatQuery.Sort.DESCENDING);
        }
        SearchFilter query = page;
        long startedAt = System.nanoTime();
        onClient(AllTheLogsClient.worker().query(query.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("AllTheLogs query failed", error);
                list.reset(List.of(), false, false);
                list.showOverlay(Component.translatable("allthelogs.status.error"));
                snapshotCurrentList();
                return;
            }
            List<DisplayRow> rows = DisplayRow.from(entries, filter);
            if (chronological) rows = ResultWindow.reversed(rows);
            boolean full = pageIsFull(rows, query);
            list.reset(rows, chronological && full, !chronological && full);
            if (chronological) list.scrollToEnd();
            list.showMatchCount(ResultWindow.matchCount(rows), elapsedMs(startedAt));
            snapshotCurrentList();
            loadMatchSummary(gen, page, startedAt);
        });
        refreshStats();
    }

    /**
     * Timeline totals after the page is on screen, so a slower summarize cannot overwrite
     * the list before rows arrive, or land on a newer search.
     */
    private void loadMatchSummary(int gen, SearchFilter page, long startedAt) {
        onClient(AllTheLogsClient.worker().summarize(page.toSummaryQuery()), (summary, error) -> {
            if (gen != generation.get() || error != null || list == null) return;
            matchSummary = summary == null ? MatchSummary.empty() : summary;
            list.setMatchSummary(matchSummary);
            list.setTotalMatchCount(matchSummary.matches(), elapsedMs(startedAt));
            snapshotCurrentList();
        });
    }

    private static long elapsedMs(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    void refreshStats() {
        if (info == null) return;
        onClient(AllTheLogsClient.worker().metadata(), (metadata, error) -> {
            if (info == null) return;
            if (error != null || metadata == null) {
                info.tooltip(List.of(
                    Component.translatable("allthelogs.meta.hint"),
                    Component.translatable("allthelogs.meta.unavailable")));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("allthelogs.meta.hint"));
            lines.addAll(StoreSummary.tooltip(metadata));
            info.tooltip(lines);
            versions = metadata.minecraftVersions();
        });
    }

    private void loadMore(MessageTimeline.Edge edge) {
        if (list.loading() || list.window().rows().isEmpty()) return;
        list.setLoading(true);
        LocalDateTime cursor = edge == MessageTimeline.Edge.AFTER
            ? list.window().lastMatchTime()
            : list.window().firstMatchTime();
        if (cursor == null) {
            list.setLoading(false);
            return;
        }
        boolean towardStart = edge == MessageTimeline.Edge.BEFORE;
        SearchFilter page = filter.withOffset(cursor);
        if (towardStart) page = page.withSort(filter.sort().opposite());
        DisplayRow.RowKey anchor = list.visibleAnchor();
        int firstVisible = list.firstVisibleIndex();
        int lastVisible = list.lastVisibleIndex();
        Set<DisplayRow.RowKey> beforeKeys = keysOf(list.window().rows());
        int gen = generation.get();
        onClient(AllTheLogsClient.worker().query(page.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("AllTheLogs page query failed", error);
                return;
            }
            List<DisplayRow> incoming = DisplayRow.from(entries, filter);
            if (towardStart) incoming = ResultWindow.reversed(incoming);
            boolean more = pageIsFull(incoming, filter);
            boolean added = incoming.stream().anyMatch(row -> !beforeKeys.contains(row.key()));
            if (!added) more = false;
            List<DisplayRow> head = towardStart ? incoming : list.window().rows();
            List<DisplayRow> tail = towardStart ? list.window().rows() : incoming;
            List<DisplayRow> merged = ResultWindow.mergeUnique(head, tail);
            int prepended = towardStart ? countNewKeys(incoming, beforeKeys) : 0;
            List<DisplayRow> trimmed = ResultWindow.trimToMatchLimit(
                merged, (int) Math.max(1, filter.limit()), firstVisible + prepended, lastVisible + prepended);
            boolean hasBefore = (towardStart ? more : list.window().hasBefore())
                || ResultWindow.trimmedHead(merged, trimmed);
            boolean hasAfter = (towardStart ? list.window().hasAfter() : more)
                || ResultWindow.trimmedTail(merged, trimmed);
            list.applyPage(trimmed, hasBefore, hasAfter, anchor);
            snapshotCurrentList();
        });
    }

    private void expandAround(DisplayRow row, MessageTimeline.Edge side) {
        int extra = MessageListLayout.extraContextLines(filter.contextLines());
        if (extra <= 0 || list == null) return;
        boolean older = MessageListLayout.expandOlderMessages(
            side == MessageTimeline.Edge.BEFORE, filter.sort() == ChatQuery.Sort.ASCENDING);
        int before = older ? extra : 0;
        int after = older ? 0 : extra;
        DisplayRow.RowKey anchor = row.key();
        onClient(AllTheLogsClient.worker().around(row.chatLog(), row.lineIndex(), before, after), (entries, error) -> {
            if (error != null || list == null) {
                if (error != null) AllTheLogsClient.LOGGER.warn("AllTheLogs expand query failed", error);
                return;
            }
            List<DisplayRow> extraRows = DisplayRow.from(entries, filter);
            List<DisplayRow> merged = ResultWindow.mergeSorted(list.window().rows(), extraRows, filter.sort());
            list.applyPage(merged, list.window().hasBefore(), list.window().hasAfter(), anchor);
            list.setScrollY(list.scrollY());
            snapshotCurrentList();
        });
    }

    private void beginScrub() {
        generation.incrementAndGet();
        list.setLoading(false);
    }

    private void jumpTo(MessageTimeline.ScrubJump jump, boolean preview) {
        LocalDateTime target = clampToBounds(jump == null ? null : jump.time());
        if (target == null && (jump == null || jump.skip() < 0)) {
            if (!preview) list.finishScrub();
            return;
        }
        SearchFilter page = filter.withoutOffset();
        ChatQuery query;
        if (jump != null && jump.skip() >= 0) {
            query = page.toQuery().withSkip(jump.skip());
        } else {
            query = page.withOffset(exclusiveOffset(target, filter.sort())).toQuery();
        }
        if (preview) {
            long cap = filter.limit() < 0 ? MessageTimeline.SCRUB_PAGE_SIZE
                : Math.min(MessageTimeline.SCRUB_PAGE_SIZE, filter.limit());
            query = query.withLimit(Math.max(8, cap));
        }
        ChatQuery requested = query;
        int gen = generation.incrementAndGet();
        if (!preview) list.setLoading(true);
        onClient(AllTheLogsClient.worker().query(requested), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                if (!preview) list.finishScrub();
                return;
            }
            List<DisplayRow> rows = DisplayRow.from(entries, filter);
            if (rows.isEmpty()) {
                if (!preview) list.finishScrub();
                return;
            }
            boolean full = requested.limit() > 0 && ResultWindow.matchCount(rows) >= requested.limit();
            double progress = jump == null ? Double.NaN : jump.progress();
            list.showAt(target, rows, pageHasBefore(filter.sort(), rows, matchSummary),
                pageHasAfter(filter.sort(), full, rows, matchSummary), progress);
            if (!preview) list.finishScrub();
            snapshotCurrentList();
        });
    }

    private LocalDateTime clampToBounds(LocalDateTime time) {
        if (time == null) return null;
        LocalDateTime oldest = matchSummary.oldest();
        LocalDateTime newest = matchSummary.newest();
        if (oldest == null || newest == null) return time;
        if (time.isBefore(oldest)) return oldest;
        if (time.isAfter(newest)) return newest;
        return time;
    }

    private void snapshotCurrentList() {
        if (list == null) return;
        restoredRows = list.window().rows();
        restoredHasBefore = list.window().hasBefore();
        restoredHasAfter = list.window().hasAfter();
        restoredScrollY = list.scrollY();
        restoredMatchCount = list.matchCount();
        restoredExactMatchCount = list.exactMatchCount();
        restoredElapsedMs = list.matchElapsedMs();
    }
}
