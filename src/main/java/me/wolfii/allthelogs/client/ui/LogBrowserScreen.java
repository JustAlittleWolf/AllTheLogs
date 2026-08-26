package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.ResultWindow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Transparent log browser. Search, filter, virtualised history, and a timeline of every hit.
 */
public final class LogBrowserScreen extends BaseOwoScreen<FlowLayout> {
    private final AtomicInteger queryGeneration = new AtomicInteger();
    private SearchFilter filter;
    private TimelineLogList list;
    private LabelComponent status;
    private FlowLayout filterPanel;
    private boolean filterOpen;

    public LogBrowserScreen() {
        super(Component.translatable("allthelogs.screen.browser"));
        this.filter = AllTheLogsClient.settings().toFilter();
    }

    private static boolean pageIsFull(List<DisplayRow> rows, SearchFilter page) {
        return ResultWindow.matchCount(rows) >= page.limit() && page.limit() > 0;
    }

    private static String formatBound(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(6);
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(8))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        root.child(buildToolbar(root));

        list = new TimelineLogList();
        list.onApproachEdge(this::loadMore);
        list.onJump(this::jumpTo);
        root.child(list.verticalSizing(Sizing.expand()));

        FlowLayout bottom = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bottom.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        bottom.child(UIComponents.button(Component.translatable("allthelogs.import.button"),
            button -> Minecraft.getInstance().gui.setScreen(new ImportScreen(this))));
        status = UIComponents.label(Component.empty());
        bottom.child(status);
        root.child(bottom);

        reload(true);
    }

    private FlowLayout buildToolbar(FlowLayout root) {
        FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bar.gap(4).verticalAlignment(VerticalAlignment.CENTER);

        TextBoxComponent search = UIComponents.textBox(Sizing.expand(), filter.text());
        search.setHint(Component.translatable("allthelogs.search.placeholder"));
        search.setMaxLength(256);
        search.onChanged().subscribe(this::onSearchChanged);
        bar.child(search);

        bar.child(UIComponents.button(Component.translatable("allthelogs.filter"), button -> toggleFilter(root, button)));
        return bar;
    }

    private void onSearchChanged(String text) {
        filter = filter.withText(text).withoutOffset();
        int generation = queryGeneration.incrementAndGet();
        CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation == queryGeneration.get()) {
                Minecraft.getInstance().execute(() -> reload(true));
            }
        });
    }

    private void toggleFilter(FlowLayout root, ButtonComponent button) {
        if (filterOpen) {
            closeFilter(root);
            return;
        }
        filterOpen = true;
        filterPanel = buildFilterPanel();
        filterPanel.positioning(Positioning.absolute(
            Math.max(8, this.width - 248),
            button.y() + button.height() + 4));
        root.child(filterPanel);
    }

    private void closeFilter(FlowLayout root) {
        if (filterPanel != null) {
            root.removeChild(filterPanel);
            filterPanel = null;
        }
        filterOpen = false;
    }

    private FlowLayout buildFilterPanel() {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(230), Sizing.content());
        panel.padding(Insets.of(8));
        panel.gap(4);
        panel.surface(Surface.flat(0xE0101010).and(Surface.outline(0xFF3C3C3C)));

        panel.child(checkbox("allthelogs.filter.regex", filter.regex(), value ->
            updateFilter(filter.withRegex(value))));
        panel.child(checkbox("allthelogs.filter.case_sensitive", filter.caseSensitive(), value ->
            updateFilter(filter.withCaseSensitive(value))));
        panel.child(labeledField("allthelogs.filter.context", String.valueOf(filter.contextLines()), text -> {
            try {
                updateFilter(filter.withContextLines(Integer.parseInt(text.trim())));
            } catch (RuntimeException ignored) {
            }
        }));
        panel.child(labeledField("allthelogs.filter.limit", String.valueOf(filter.limit()), text -> {
            try {
                int limit = Integer.parseInt(text.trim());
                if (limit > 0) updateFilter(filter.withLimit(limit));
            } catch (RuntimeException ignored) {
            }
        }));

        panel.child(UIComponents.label(Component.translatable("allthelogs.filter.sort")));
        FlowLayout sortRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        sortRow.gap(4);
        sortRow.child(sortButton("allthelogs.filter.sort.ascending", ChatQuery.Sort.ASCENDING));
        sortRow.child(sortButton("allthelogs.filter.sort.descending", ChatQuery.Sort.DESCENDING));
        panel.child(sortRow);

        panel.child(dateField("allthelogs.filter.from", filter.startingAt(), parsed ->
            updateFilter(filter.withStartingAt(parsed))));
        panel.child(dateField("allthelogs.filter.until", filter.upUntil(), parsed ->
            updateFilter(filter.withUpUntil(parsed))));
        panel.child(UIComponents.label(Component.translatable("allthelogs.filter.date_hint"))
            .color(Color.ofRgb(0x888888)));
        return panel;
    }

    private CheckboxComponent checkbox(String key, boolean checked, Consumer<Boolean> onChange) {
        CheckboxComponent box = UIComponents.checkbox(Component.translatable(key));
        box.checked(checked);
        box.onChanged(onChange::accept);
        return box;
    }

    private ButtonComponent sortButton(String key, ChatQuery.Sort sort) {
        return UIComponents.button(Component.translatable(key), button -> updateFilter(filter.withSort(sort)));
    }

    private FlowLayout dateField(String key, LocalDateTime value, Consumer<LocalDateTime> onParsed) {
        return labeledField(key, formatBound(value), text -> {
            if (!DateParser.isBlankOrValid(text)) return;
            onParsed.accept(DateParser.parse(text).orElse(null));
        });
    }

    private FlowLayout labeledField(String key, String value, Consumer<String> onChange) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(32);
        box.onChanged().subscribe(onChange::accept);
        row.child(box);
        return row;
    }

    private void updateFilter(SearchFilter next) {
        filter = next.withoutOffset();
        persistAndReload();
    }

    private void persistAndReload() {
        AllTheLogsClient.settings().apply(filter);
        AllTheLogsClient.saveSettings();
        reload(true);
    }

    private void reload(boolean resetTimeline) {
        if (list == null) return;
        int generation = queryGeneration.incrementAndGet();
        list.setLoading(true);
        status.text(Component.translatable("allthelogs.status.loading"));
        SearchFilter page = filter.withoutOffset();
        AllTheLogsClient.worker().query(page.toQuery()).whenComplete((entries, error) -> {
            Minecraft.getInstance().execute(() -> {
                if (generation != queryGeneration.get()) return;
                list.setLoading(false);
                if (error != null) {
                    AllTheLogsClient.LOGGER.warn("AllTheLogs query failed", error);
                    status.text(Component.translatable("allthelogs.status.error"));
                    return;
                }
                List<DisplayRow> rows = DisplayRow.from(entries, page);
                list.reset(rows, false, pageIsFull(rows, page));
                status.text(Component.literal(rows.size() + " lines"));
            });
        });
        if (resetTimeline) {
            AllTheLogsClient.worker().query(page.toTimelineQuery()).whenComplete((entries, error) -> {
                Minecraft.getInstance().execute(() -> {
                    if (error != null || list == null) return;
                    list.setMarkers(entries.stream().map(ChatEntry::timestamp).toList());
                });
            });
        }
    }

    private void loadMore(TimelineLogList.Edge edge) {
        if (list.loading() || list.window().rows().isEmpty()) return;
        list.setLoading(true);
        LocalDateTime cursor = edge == TimelineLogList.Edge.AFTER
            ? list.window().lastMatchTime()
            : list.window().firstMatchTime();
        if (cursor == null) {
            list.setLoading(false);
            return;
        }
        boolean olderPage = edge == TimelineLogList.Edge.BEFORE;
        SearchFilter page = filter.withOffset(cursor);
        if (olderPage) page = page.withSort(filter.sort().opposite());
        DisplayRow.RowKey anchor = list.window().keyAtPixel(list.scrollY(), TimelineLogList.ROW_HEIGHT);
        int firstVisible = (int) Math.floor(list.scrollY() / TimelineLogList.ROW_HEIGHT);
        int lastVisible = firstVisible + Math.max(1, list.height() / TimelineLogList.ROW_HEIGHT);
        AllTheLogsClient.worker().query(page.toQuery()).whenComplete((entries, error) -> {
            Minecraft.getInstance().execute(() -> {
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
                boolean hasBefore = list.window().hasBefore() || (olderPage && more);
                boolean hasAfter = list.window().hasAfter() || (!olderPage && more);
                if (olderPage && !more) hasBefore = false;
                if (!olderPage && !more) hasAfter = false;
                list.applyPage(trimmed, hasBefore, hasAfter, anchor);
            });
        });
    }

    private void jumpTo(LocalDateTime time) {
        SearchFilter page = filter.withOffset(time.minusNanos(1));
        list.setLoading(true);
        AllTheLogsClient.worker().query(page.toQuery()).whenComplete((entries, error) -> {
            Minecraft.getInstance().execute(() -> {
                list.setLoading(false);
                if (error != null) return;
                List<DisplayRow> rows = DisplayRow.from(entries, filter);
                list.reset(rows, true, pageIsFull(rows, filter));
            });
        });
    }
}
