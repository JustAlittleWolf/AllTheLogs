package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.list.ResultWindow;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.text.StoreSummary;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    SearchFilter filter() {
        return filter;
    }

    List<String> versions() {
        return versions;
    }

    void attach(MessageTimeline list, ButtonComponent info) {
        this.list = list;
        this.info = info;
        list.setContextLines(filter.contextLines());
        list.onApproachEdge(this::loadMore);
        list.onJump(this::jumpTo);
        list.onExpand(this::expandAround);
        list.onScrubBegin(this::beginScrub);
    }

    void updateFilter(SearchFilter next) {
        filter = next.withoutOffset();
        if (list != null) list.setContextLines(filter.contextLines());
    }

    void setFilter(SearchFilter next) {
        updateFilter(next);
        reload(true);
    }

    int bumpGeneration() {
        return generation.incrementAndGet();
    }

    int currentGeneration() {
        return generation.get();
    }

    void reload(boolean resetTimeline) {
        if (list == null) return;
        int gen = generation.incrementAndGet();
        list.setLoading(true);
        refreshStats();
        SearchFilter page = filter.withoutOffset();
        long startedAt = System.nanoTime();
        onClient(AllTheLogsClient.worker().query(page.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("AllTheLogs query failed", error);
                list.reset(List.of(), false, false);
                list.showOverlay(Component.translatable("allthelogs.status.error"));
                return;
            }
            List<DisplayRow> rows = DisplayRow.from(entries, page);
            list.reset(rows, false, pageIsFull(rows, page));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            list.showMatchCount(ResultWindow.matchCount(rows), elapsedMs);
        });
        if (resetTimeline) {
            onClient(AllTheLogsClient.worker().matchBounds(page.toTimelineQuery()), (bounds, error) -> {
                if (error != null || list == null) return;
                list.setMatchBounds(bounds);
            });
        }
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
        boolean olderPage = edge == MessageTimeline.Edge.BEFORE;
        SearchFilter page = filter.withOffset(cursor);
        if (olderPage) page = page.withSort(filter.sort().opposite());
        DisplayRow.RowKey anchor = list.visibleAnchor();
        int firstVisible = list.firstVisibleIndex();
        int lastVisible = list.lastVisibleIndex();
        int gen = generation.get();
        onClient(AllTheLogsClient.worker().query(page.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("AllTheLogs page query failed", error);
                return;
            }
            List<DisplayRow> incoming = DisplayRow.from(entries, filter);
            if (olderPage) incoming = ResultWindow.reversed(incoming);
            boolean more = pageIsFull(incoming, filter);
            List<DisplayRow> older = olderPage ? incoming : list.window().rows();
            List<DisplayRow> newer = olderPage ? list.window().rows() : incoming;
            List<DisplayRow> trimmed = ResultWindow.trimToMatchLimit(
                ResultWindow.mergeUnique(older, newer),
                (int) Math.max(1, filter.limit()), firstVisible, lastVisible);
            boolean hasBefore = olderPage ? more : list.window().hasBefore();
            boolean hasAfter = olderPage ? list.window().hasAfter() : more;
            list.applyPage(trimmed, hasBefore, hasAfter, anchor);
        });
    }

    private void expandAround(DisplayRow row) {
        int extra = MessageListLayout.extraContextLines(filter.contextLines());
        if (extra <= 0 || list == null) return;
        DisplayRow.RowKey anchor = row.key();
        onClient(AllTheLogsClient.worker().around(row.chatLog(), row.lineIndex(), extra), (entries, error) -> {
            if (error != null || list == null) {
                if (error != null) AllTheLogsClient.LOGGER.warn("AllTheLogs expand query failed", error);
                return;
            }
            List<DisplayRow> extraRows = DisplayRow.from(entries, filter);
            List<DisplayRow> merged = ResultWindow.mergeSorted(list.window().rows(), extraRows, filter.sort());
            list.applyPage(merged, list.window().hasBefore(), list.window().hasAfter(), anchor);
            list.setScrollY(list.scrollY());
        });
    }

    private void beginScrub() {
        generation.incrementAndGet();
        list.setLoading(false);
    }

    private void jumpTo(LocalDateTime time, boolean preview) {
        SearchFilter page = filter.withOffset(time.minusNanos(1));
        if (preview) {
            long cap = filter.limit() < 0 ? MessageTimeline.SCRUB_PAGE_SIZE
                : Math.min(MessageTimeline.SCRUB_PAGE_SIZE, filter.limit());
            page = page.withLimit(Math.max(8, cap));
        }
        SearchFilter query = page;
        int gen = generation.incrementAndGet();
        if (!preview) list.setLoading(true);
        onClient(AllTheLogsClient.worker().query(query.toQuery()), (entries, error) -> {
            if (gen != generation.get()) return;
            list.setLoading(false);
            if (error != null) {
                if (!preview) list.finishScrub();
                return;
            }
            List<DisplayRow> rows = DisplayRow.from(entries, filter);
            list.showAt(time, rows, true, pageIsFull(rows, query));
            if (!preview) list.finishScrub();
        });
    }

    private static boolean pageIsFull(List<DisplayRow> rows, SearchFilter page) {
        return ResultWindow.matchCount(rows) >= page.limit() && page.limit() > 0;
    }

    private static <T> void onClient(CompletableFuture<T> future, BiConsumer<T, Throwable> handler) {
        future.whenComplete((value, error) -> Minecraft.getInstance().execute(() -> handler.accept(value, error)));
    }
}
