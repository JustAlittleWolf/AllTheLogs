package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.MessageListLayout;
import me.wolfii.allthelogs.client.view.ResultWindow;
import me.wolfii.allthelogs.data.ChatQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Transparent log browser. Search, filter, virtualised history, and a timeline of every hit.
 */
public final class LogBrowserScreen extends BaseOwoScreen<StackLayout> {
    private static final int SEARCH_DEBOUNCE_MS = 100;

    private final Screen parent;
    private final AtomicInteger queryGeneration = new AtomicInteger();
    private SearchFilter filter = SearchFilter.defaults();
    private TimelineLogList list;
    private TextBoxComponent search;
    private ButtonComponent info;
    private ParentUIComponent filterPanel;
    private ButtonComponent oldestFirst;
    private ButtonComponent newestFirst;
    private ButtonComponent versionButton;
    private ParentUIComponent versionMenu;
    private List<String> versions = List.of();
    private boolean filterOpen;
    private StackLayout overlays;
    private FlowLayout content;

    public LogBrowserScreen() {
        this(null);
    }

    public LogBrowserScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.browser"));
        this.parent = parent;
    }

    private static boolean pageIsFull(List<DisplayRow> rows, SearchFilter page) {
        return ResultWindow.matchCount(rows) >= page.limit() && page.limit() > 0;
    }

    private static String formatBound(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.overlays = root;
        content = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        content.gap(6);
        content.allowOverflow(true);
        content.surface(Surface.blur(5, 10).and(BrowserPanels.overlay()))
            .padding(Insets.of(8))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        content.child(buildToolbar());

        list = new TimelineLogList();
        list.setContextLines(filter.contextLines());
        list.onApproachEdge(this::loadMore);
        list.onJump(this::jumpTo);
        list.onExpand(this::expandAround);
        list.onScrubBegin(this::beginScrub);
        content.child(list.verticalSizing(Sizing.expand()));
        root.child(content);
    }

    @Override
    protected void init() {
        super.init();
        if (search != null && search.focusHandler() != null) {
            search.focusHandler().focus(search, UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
        if (list != null) {
            reload(true);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isSelectAll() && list != null && !searchHasFocus()) {
            return list.selectAllOnVisibleDate();
        }
        return super.keyPressed(event);
    }

    private boolean searchHasFocus() {
        if (search == null || search.focusHandler() == null) return false;
        return search.focusHandler().focused() == search;
    }

    private FlowLayout buildToolbar() {
        FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bar.gap(4).verticalAlignment(VerticalAlignment.CENTER);

        search = UIComponents.textBox(Sizing.expand(), filter.text());
        search.setHint(Component.translatable("allthelogs.search.placeholder"));
        search.setMaxLength(256);
        search.onChanged().subscribe(this::onSearchChanged);
        bar.child(search);

        bar.child(UIComponents.button(Component.translatable("allthelogs.filter"),
            button -> toggleFilter(button)));
        bar.child(UIComponents.button(Component.translatable("allthelogs.import.button"),
            button -> Minecraft.getInstance().gui.setScreen(new ImportScreen(this))));

        info = UIComponents.button(Component.translatable("allthelogs.meta.marker"), button -> {
        });
        info.tooltip(List.of(
            Component.translatable("allthelogs.meta.hint"),
            Component.translatable("allthelogs.meta.unavailable")));
        info.horizontalSizing(Sizing.fixed(20));
        bar.child(info);
        return bar;
    }

    private void onSearchChanged(String text) {
        filter = filter.withText(text).withoutOffset();
        int generation = queryGeneration.incrementAndGet();
        CompletableFuture.delayedExecutor(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation == queryGeneration.get()) {
                Minecraft.getInstance().execute(() -> reload(true));
            }
        });
    }

    private void toggleFilter(ButtonComponent button) {
        if (filterOpen) {
            closeFilter();
            return;
        }
        filterOpen = true;
        filterPanel = buildFilterPanel();
        filterPanel.positioning(Positioning.absolute(
            Math.max(8, this.width - 258),
            button.y() + button.height() + 4));
        overlays.child(filterPanel);
    }

    private void closeFilter() {
        if (filterPanel != null) {
            overlays.removeChild(filterPanel);
            filterPanel = null;
        }
        oldestFirst = null;
        newestFirst = null;
        versionButton = null;
        closeVersionMenu();
        filterOpen = false;
    }

    private ParentUIComponent buildFilterPanel() {
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        content.padding(Insets.of(8));
        content.gap(4);

        content.child(checkbox("allthelogs.filter.regex", filter.regex(), value ->
            updateFilter(filter.withRegex(value))));
        content.child(checkbox("allthelogs.filter.case_sensitive", filter.caseSensitive(), value ->
            updateFilter(filter.withCaseSensitive(value))));
        content.child(versionRow());
        content.child(labeledField("allthelogs.filter.context", String.valueOf(filter.contextLines()), text -> {
            try {
                updateFilter(filter.withContextLines(Integer.parseInt(text.trim())));
            } catch (RuntimeException ignored) {
            }
        }));

        content.child(UIComponents.label(Component.translatable("allthelogs.filter.sort")));
        FlowLayout sortRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        sortRow.gap(4);
        oldestFirst = sortButton("allthelogs.filter.sort.ascending", ChatQuery.Sort.ASCENDING);
        newestFirst = sortButton("allthelogs.filter.sort.descending", ChatQuery.Sort.DESCENDING);
        sortRow.child(oldestFirst);
        sortRow.child(newestFirst);
        content.child(sortRow);
        syncSortButtons();

        content.child(dateField("allthelogs.filter.from", filter.startingAt(), parsed ->
            updateFilter(filter.withStartingAt(parsed))));
        content.child(dateField("allthelogs.filter.until", filter.upUntil(), parsed ->
            updateFilter(filter.withUpUntil(parsed))));
        content.child(UIComponents.label(Component.translatable("allthelogs.filter.date_hint"))
            .color(Color.ofRgb(0x888888)));

        int panelHeight = Math.max(96, Math.min(this.height - 40, 280));
        ScrollContainer<FlowLayout> panel = UIContainers.verticalScroll(
            Sizing.fixed(240), Sizing.fixed(panelHeight), content);
        panel.scrollbar(OverflowScrollbar.vanillaFlat());
        panel.surface(BrowserPanels.card());
        return panel;
    }

    private CheckboxComponent checkbox(String key, boolean checked, Consumer<Boolean> onChange) {
        CheckboxComponent box = UIComponents.checkbox(Component.translatable(key));
        box.checked(checked);
        box.onChanged(onChange::accept);
        return box;
    }

    private FlowLayout versionRow() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.filter.version")));
        versionButton = UIComponents.button(versionLabel(), button -> toggleVersionMenu(button));
        versionButton.horizontalSizing(Sizing.fill());
        row.child(versionButton);
        return row;
    }

    private Component versionLabel() {
        if (!filter.hasVersion()) {
            return Component.translatable("allthelogs.filter.version.all");
        }
        return Component.literal(filter.version());
    }

    private void toggleVersionMenu(ButtonComponent button) {
        if (versionMenuOpen()) {
            closeVersionMenu();
            return;
        }
        openVersionMenu(button);
    }

    private boolean versionMenuOpen() {
        return overlays != null && versionMenu != null && overlays.children().contains(versionMenu);
    }

    private void closeVersionMenu() {
        if (versionMenuOpen()) {
            overlays.removeChild(versionMenu);
        }
        versionMenu = null;
    }

    private void openVersionMenu(ButtonComponent button) {
        if (overlays == null) return;
        closeVersionMenu();
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        items.child(versionChoice(Component.translatable("allthelogs.filter.version.all"), null));
        for (String version : versions) {
            items.child(versionChoice(Component.literal(version), version));
        }
        int width = Math.max(120, button.width());
        int maxHeight = Math.max(48, Math.min(180, this.height - button.y() - button.height() - 12));
        ScrollContainer<FlowLayout> menu = UIContainers.verticalScroll(
            Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        menu.scrollbar(OverflowScrollbar.vanillaFlat());
        menu.surface(BrowserPanels.menu());
        int menuX = Math.min(button.x(), Math.max(0, this.width - width - 4));
        int menuY = button.y() + button.height();
        if (menuY + maxHeight > this.height - 4) {
            menuY = Math.max(4, button.y() - maxHeight);
        }
        menu.positioning(Positioning.absolute(menuX, menuY));
        versionMenu = menu;
        overlays.child(menu);
    }

    private ButtonComponent versionChoice(Component label, String version) {
        ButtonComponent choice = UIComponents.button(label, ignored -> {
            closeVersionMenu();
            updateFilter(filter.withVersion(version));
        });
        choice.horizontalSizing(Sizing.fill());
        return choice;
    }

    private ButtonComponent sortButton(String key, ChatQuery.Sort sort) {
        return UIComponents.button(Component.translatable(key), button -> {
            if (filter.sort() == sort) return;
            updateFilter(filter.withSort(sort));
        });
    }

    private void syncSortButtons() {
        if (oldestFirst == null || newestFirst == null) return;
        oldestFirst.active(filter.sort() != ChatQuery.Sort.ASCENDING);
        newestFirst.active(filter.sort() != ChatQuery.Sort.DESCENDING);
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
        if (list != null) list.setContextLines(filter.contextLines());
        syncSortButtons();
        if (versionButton != null) versionButton.setMessage(versionLabel());
        reload(true);
    }

    private void reload(boolean resetTimeline) {
        if (list == null) return;
        int generation = queryGeneration.incrementAndGet();
        list.setLoading(true);
        refreshStats();
        SearchFilter page = filter.withoutOffset();
        long startedAt = System.nanoTime();
        onClient(AllTheLogsClient.worker().query(page.toQuery()), (entries, error) -> {
            if (generation != queryGeneration.get()) return;
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

    private void refreshStats() {
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
            lines.addAll(StoreInfo.tooltip(metadata));
            info.tooltip(lines);
            versions = metadata.minecraftVersions();
        });
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
        DisplayRow.RowKey anchor = list.visibleAnchor();
        int firstVisible = list.firstVisibleIndex();
        int lastVisible = list.lastVisibleIndex();
        int generation = queryGeneration.get();
        onClient(AllTheLogsClient.worker().query(page.toQuery()), (entries, error) -> {
            if (generation != queryGeneration.get()) return;
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
        queryGeneration.incrementAndGet();
        list.setLoading(false);
    }

    private void jumpTo(LocalDateTime time, boolean preview) {
        SearchFilter page = filter.withOffset(time.minusNanos(1));
        if (preview) {
            long cap = filter.limit() < 0 ? TimelineLogList.SCRUB_PAGE_SIZE
                : Math.min(TimelineLogList.SCRUB_PAGE_SIZE, filter.limit());
            page = page.withLimit(Math.max(8, cap));
        }
        SearchFilter query = page;
        int generation = queryGeneration.incrementAndGet();
        if (!preview) list.setLoading(true);
        onClient(AllTheLogsClient.worker().query(query.toQuery()), (entries, error) -> {
            if (generation != queryGeneration.get()) return;
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

    private <T> void onClient(CompletableFuture<T> future, BiConsumer<T, Throwable> handler) {
        future.whenComplete((value, error) -> Minecraft.getInstance().execute(() -> handler.accept(value, error)));
    }
}
