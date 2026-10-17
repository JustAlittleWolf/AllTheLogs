package me.wolfii.allthelogs.client.ui.screen.browser;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.AllTheLogsScreens;
import me.wolfii.allthelogs.client.config.AllTheLogsConfig;
import me.wolfii.allthelogs.client.config.BrowserSession;
import me.wolfii.allthelogs.client.export.ExportMetadata;
import me.wolfii.allthelogs.client.export.ExportProgress;
import me.wolfii.allthelogs.client.export.MessageExport;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageSelection;
import me.wolfii.allthelogs.client.search.ActiveFilters;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.screen.playtime.PlaytimeScreen;
import me.wolfii.allthelogs.client.ui.popup.ModalDialog;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.DecoratedSearchField;
import me.wolfii.allthelogs.client.ui.widget.timeline.MessageTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Transparent log browser: search bar, side filter panel, virtualised history, and a timeline of every hit.
 */
public final class LogBrowserScreen extends BaseOwoScreen<StackLayout> {
    /** How long typing must pause before the search runs, here and on the cleanup screen. */
    static final int SEARCH_DEBOUNCE_MS = 250;
    /** Above this many messages, the export shade warns that the write may take a while. */
    private static final int LARGE_EXPORT = 10_000;

    private final Screen parent;
    private final LogBrowserQueries queries;
    private MessageTimeline list;
    private SearchBar searchBar;
    private DecoratedSearchField search;
    private ButtonComponent filterButton;
    private FilterOverlay filters;
    private StackLayout overlays;
    private DropdownComponent messageMenu;
    private BrowserToolsMenu tools;
    private ModalDialog dialog;
    private FlowLayout exporting;
    private LabelComponent exportCounts;
    private BoxComponent exportFill;
    private LabelComponent exportLargeLabel;
    private boolean exportLarge;
    private long exportTotal = -1;
    private ExportProgress exportProgress;
    private final AtomicReference<ExportSnapshot> latestExport = new AtomicReference<>();
    private final AtomicBoolean exportProgressScheduled = new AtomicBoolean();
    private FlowLayout exportNotice;
    private int exportToken;
    private int noticeToken;
    private boolean exportBusy;
    private int sessionResets = BrowserSession.resets();

    public LogBrowserScreen() {
        this(null);
    }

    public LogBrowserScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.browser"));
        this.parent = parent;
        this.queries = new LogBrowserQueries(BrowserSession.openingFilter());
        this.queries.restoreSessionLocation();
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.overlays = root;
        root.allowOverflow(true);
        FlowLayout chrome = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill());
        chrome.gap(2);
        chrome.padding(Insets.none());

        FlowLayout content = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill());
        content.gap(6);
        content.allowOverflow(true);
        content.surface(Surface.blur(5, 10).and(PanelSurfaces.overlay()))
            .padding(Insets.of(8))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        list = new MessageTimeline();
        list.setMessageFontSize(AllTheLogsConfig.get().messageFontSize());
        list.onContextMenu(this::openMessageMenu);
        list.onDismissContextMenu(this::closeMessageMenu);
        tools = new BrowserToolsMenu(overlays, () -> this.width, () -> this.height,
            () -> list.hasSelectedText(), () -> exportBusy, this::closeMessageMenu,
            this::openScripts, this::openImport, this::openPlaytime, this::openCleanup, this::openOptimize,
            this::startExport);
        dialog = new ModalDialog(overlays, () -> this.width);
        list.setTrackTopInset(BrowserToolsMenu.BUTTON_SIZE);
        StackLayout messages = UIContainers.stack(Sizing.fill(), Sizing.expand());
        messages.horizontalAlignment(HorizontalAlignment.LEFT).verticalAlignment(VerticalAlignment.TOP);
        list.sizing(Sizing.fill(), Sizing.fill());
        messages.child(list);
        ButtonComponent menuButton = tools.button();
        menuButton.positioning(Positioning.relative(100, 0));
        messages.child(menuButton);
        FlowLayout toolbar = buildToolbar();
        queries.attach(list, tools::setDatabaseInfo);
        content.child(messages);
        content.child(toolbar);
        chrome.child(content);

        filters = new FilterOverlay(chrome, overlays, () -> this.width, () -> this.height,
            queries::filter, queries.catalog(), this::applyFilter);
        root.child(chrome);
        filters.restore();
        searchBar.sync();
    }

    @Override
    protected void init() {
        super.init();
        boolean startOver = sessionResets != BrowserSession.resets();
        if (startOver) startOver();
        if (searchBar != null) searchBar.reveal();
        if (search != null && search.focusHandler() != null) {
            search.focusHandler().focus(search, UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
        if (!startOver && list != null && queries.consumeReload()) {
            queries.reload();
        }
    }

    /**
     * Drops the search and filters after an import or a cleanup changed the stored messages while this
     * screen waited underneath, and loads the newest messages.
     */
    private void startOver() {
        sessionResets = BrowserSession.resets();
        queries.startOver(BrowserSession.openingFilter());
        refreshSearchDecorations();
        if (filters != null) filters.syncFromFilter();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (tools != null && overlays != null) {
            boolean scrollbarDrag = list != null && list.draggingScrollbar();
            overlays.queue(() -> tools.sync(mouseX, mouseY, scrollbarDrag));
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (list != null && list.verticalCursor()) {
            graphics.requestCursor(CursorTypes.RESIZE_NS);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) {
            return;
        }
        this.extractPanorama(graphics, delta);
    }

    @Override
    public void tick() {
        super.tick();
        drainExportProgress();
        queries.pollLatest(!exportBusy && messageMenu == null && (tools == null || !tools.isOpen())
            && (filters == null || !filters.listOpen()) && (dialog == null || !dialog.isOpen()));
    }

    @Override
    public void removed() {
        queries.pauseLatest();
        super.removed();
    }

    @Override
    public void onClose() {
        BrowserSession.remember(queries.filter());
        queries.rememberSessionLocation();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (filters != null) filters.closeListsOnClickOutside(event.x(), event.y());
        if (tools != null) tools.closeOnClickOutside(event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dialog != null && dialog.isOpen()) {
            if (event.isEscape() && !dialog.working()) dialog.close();
            return true;
        }
        if (event.isEscape() && tools != null && tools.isOpen()) {
            tools.close();
            return true;
        }
        if (event.isEscape() && filters != null && filters.listOpen()) {
            filters.closeLists();
            return true;
        }
        if (event.isSelectAll() && list != null && !searchHasFocus() && !filterOwnsFocus()) {
            return list.selectAllOnVisibleDate();
        }
        return super.keyPressed(event);
    }

    private boolean filterOwnsFocus() {
        if (filters == null || search == null || search.focusHandler() == null) return false;
        return filters.owns(search.focusHandler().focused());
    }

    private boolean searchHasFocus() {
        if (search == null || search.focusHandler() == null) return false;
        return search.focusHandler().focused() == search;
    }

    private FlowLayout buildToolbar() {
        FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bar.gap(4).verticalAlignment(VerticalAlignment.BOTTOM);

        searchBar = new SearchBar(queries::filter, Component.translatable("allthelogs.search.placeholder"),
            this::applySearchText);
        search = searchBar.field();
        bar.child(searchBar.component());

        filterButton = UIComponents.button(ActiveFilters.buttonLabel(ActiveFilters.of(queries.filter()).size()),
            button -> {
                filters.toggle();
                refreshSearchDecorations();
            });
        bar.child(filterButton);
        refreshFilterButton();
        return bar;
    }

    private void applySearchText(String text) {
        if (text.equals(queries.filter().text())) return;
        queries.updateFilter(queries.filter().withText(text));
        BrowserSession.remember(queries.filter());
        searchBar.refreshColor();
        if (!queries.filter().canQuery()) {
            queries.bumpGeneration();
            return;
        }
        int generation = queries.bumpGeneration();
        CompletableFuture.delayedExecutor(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation == queries.currentGeneration()) {
                Minecraft.getInstance().execute(() -> queries.reload());
            }
        });
    }

    /**
     * Searches again with {@code next}. A filter equal to the current one, e.g. from clearing a field that is
     * already empty, changes nothing, so the list keeps its place.
     */
    private void applyFilter(SearchFilter next) {
        if (next.equals(queries.filter())) return;
        queries.setFilter(next);
        BrowserSession.remember(next);
        refreshSearchDecorations();
        if (filters != null) filters.syncFromFilter();
    }

    private void refreshSearchDecorations() {
        if (searchBar != null) searchBar.sync();
        refreshFilterButton();
    }

    private void refreshFilterButton() {
        if (filterButton == null) return;
        List<ActiveFilters.Entry> active = ActiveFilters.of(queries.filter());
        Component label = ActiveFilters.buttonLabel(active.size());
        if (!label.getString().equals(filterButton.getMessage().getString())) {
            filterButton.setMessage(label);
            ParentUIComponent parent = filterButton.parent();
            if (parent != null) parent.onChildMutated(filterButton);
        }
    }

    private void closeMessageMenu() {
        if (messageMenu == null || overlays == null) return;
        if (overlays.children().contains(messageMenu)) {
            overlays.removeChild(messageMenu);
        }
        messageMenu = null;
    }

    private void openMessageMenu(DisplayRow row, MessageSelection selection, List<DisplayRow> rows,
                                 double screenX, double screenY) {
        if (tools != null) tools.close();
        closeMessageMenu();
        messageMenu = DropdownComponent.openContextMenu(this, overlays, StackLayout::child, screenX, screenY, menu -> {
            if (!selection.isEmpty()) {
                menu.button(Component.translatable("allthelogs.menu.copy_selection"), ignored -> {
                    closeMessageMenu();
                    Minecraft.getInstance().keyboardHandler.setClipboard(selection.copy(rows));
                });
            }
            menu.button(Component.translatable("allthelogs.menu.copy_message"), ignored -> {
                closeMessageMenu();
                Minecraft.getInstance().keyboardHandler.setClipboard(row.message());
            });
            menu.button(Component.translatable("allthelogs.menu.filter_day"), ignored -> {
                closeMessageMenu();
                applyFilter(queries.filter().withDay(row.entry().timestamp().toLocalDate()));
            });
            if (row.entry().serverOrWorld() != null) {
                menu.button(Component.translatable("allthelogs.menu.filter_server"), ignored -> {
                    closeMessageMenu();
                    applyFilter(queries.filter().withServerOrWorld(row.entry().serverOrWorld()));
                });
            }
        });
    }

    private void openScripts() {
        if (overlays != null) {
            overlays.queue(() -> {
                if (tools != null) tools.close();
                AllTheLogsScreens.openScripts(this);
            });
            return;
        }
        if (tools != null) tools.close();
        AllTheLogsScreens.openScripts(this);
    }

    private void openPlaytime() {
        if (overlays != null) {
            overlays.queue(() -> {
                if (tools != null) tools.close();
                showPlaytime();
            });
            return;
        }
        showPlaytime();
    }

    private void showPlaytime() {
        if (tools != null) tools.close();
        Minecraft.getInstance().gui.setScreen(new PlaytimeScreen(this, filter -> {
            showFiltered(filter);
            Minecraft.getInstance().gui.setScreen(this);
        }));
    }

    private void openCleanup() {
        if (overlays != null) {
            overlays.queue(this::showCleanup);
            return;
        }
        showCleanup();
    }

    private void showCleanup() {
        if (tools != null) tools.close();
        Minecraft.getInstance().gui.setScreen(new CleanupScreen(this, queries.filter(), queries.catalog()));
    }

    /**
     * Applies a filter the next time this screen is shown. Used when playtime sends the user to messages.
     */
    public void showFiltered(SearchFilter next) {
        queries.updateFilter(next);
        BrowserSession.remember(next);
        queries.markReload();
    }

    private void openImport() {
        if (overlays != null) {
            overlays.queue(this::showImport);
            return;
        }
        showImport();
    }

    private void showImport() {
        if (tools != null) tools.close();
        queries.markReload();
        AllTheLogsScreens.openImport(this);
    }

    private void openOptimize() {
        if (overlays != null) {
            overlays.queue(this::askOptimize);
            return;
        }
        askOptimize();
    }

    /**
     * Asks before optimizing the whole store, because it can take minutes and blocks the screen meanwhile.
     */
    private void askOptimize() {
        if (tools != null) tools.close();
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) {
            showExportNotice(Component.translatable("allthelogs.meta.unavailable"), true);
            return;
        }
        dialog.confirm(Component.translatable("allthelogs.optimize.title"),
            Component.translatable("allthelogs.optimize.warning"),
            Component.translatable("allthelogs.optimize.start"), this::optimize);
    }

    private void optimize() {
        dialog.working(Component.translatable("allthelogs.status.optimizing"),
            Component.translatable("allthelogs.optimize.working"));
        AllTheLogsClient.worker().optimize().whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            dialog.close();
            if (error != null) AllTheLogsClient.LOGGER.warn("Could not optimize the store", error);
            if (Minecraft.getInstance().gui.screen() != this) return;
            queries.refreshStats();
            showExportNotice(Component.translatable(error == null ? "allthelogs.optimize.done" : "allthelogs.optimize.failed"),
                error != null);
        }));
    }

    private void startExport(BrowserToolsMenu.Scope scope, MessageExport.Format format) {
        if (overlays != null) {
            overlays.queue(() -> performExport(scope, format));
            return;
        }
        performExport(scope, format);
    }

    private void performExport(BrowserToolsMenu.Scope scope, MessageExport.Format format) {
        tools.close();
        hideExportNotice();
        if (scope == BrowserToolsMenu.Scope.QUERY) {
            Component blocked = queryBlockedReason();
            if (blocked != null) {
                showExportNotice(blocked, true);
                return;
            }
        }
        exportBusy = true;
        exportLarge = false;
        exportProgress = null;
        latestExport.set(null);
        int token = ++exportToken;
        long known = exportSize(scope);
        exportTotal = known;
        if (known > LARGE_EXPORT) showLargeExportWarning();
        else if (scope == BrowserToolsMenu.Scope.QUERY) countQueryForWarning(token);
        CompletableFuture<List<MessageExport.Line>> lines = switch (scope) {
            case SELECTION -> CompletableFuture.completedFuture(MessageExport.fromRows(list.selectedRows()));
            case VISIBLE -> CompletableFuture.completedFuture(MessageExport.fromRows(list.visibleRows()));
            case QUERY -> AllTheLogsClient.worker().findEntries(queries.filter().toSummaryQuery())
                .thenApply(MessageExport::fromQuery);
        };
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() ->
            Minecraft.getInstance().execute(() -> {
                if (token != exportToken || !exportBusy || Minecraft.getInstance().gui.screen() != this) return;
                showExporting();
            }));
        ExportMetadata metadata = exportMetadata(scope);
        lines.thenApplyAsync(exported -> MessageExport.save(format, exported, metadata,
                progress -> reportExport(token, progress)), MessageExport.executor())
            .whenComplete((path, error) -> Minecraft.getInstance().execute(() -> finishExport(token, path, error)));
    }

    private ExportMetadata exportMetadata(BrowserToolsMenu.Scope scope) {
        SearchFilter filter = queries.filter();
        String scopeName = switch (scope) {
            case SELECTION -> "selection";
            case VISIBLE -> "visible";
            case QUERY -> "query";
        };
        return new ExportMetadata(scopeName, filter.text(), filter.regex(), filter.caseSensitive(),
            filter.contextLines(), filter.sort().name().toLowerCase(Locale.ROOT), filter.startingAt(),
            filter.upUntil(), ActiveFilters.describe(filter.versions(), Function.identity()),
            ActiveFilters.describe(filter.serversOrWorlds(), FilterOverlay::placeName));
    }

    private Component queryBlockedReason() {
        if (AllTheLogsClient.worker() == null || !AllTheLogsClient.worker().isOpen()) {
            return Component.translatable("allthelogs.meta.unavailable");
        }
        Component regexError = SearchBar.regexError(queries.filter());
        if (regexError != null) return regexError;
        if (!queries.filter().canQuery()) return Component.translatable("allthelogs.status.error");
        return null;
    }

    private void finishExport(int token, Path path, Throwable error) {
        if (token != exportToken) return;
        exportBusy = false;
        hideExporting();
        if (Minecraft.getInstance().gui.screen() != this) return;
        if (error != null) {
            AllTheLogsClient.LOGGER.warn("Could not export messages", error);
            showExportNotice(Component.translatable("allthelogs.export.failed"), true);
            return;
        }
        showExportNotice(Component.translatable("allthelogs.export.saved", path.toAbsolutePath().toString()), false);
    }

    /**
     * Messages this export will write, or {@code -1} when a search-query total is not known yet.
     */
    private long exportSize(BrowserToolsMenu.Scope scope) {
        return switch (scope) {
            case SELECTION -> list.selectedRows().size();
            case VISIBLE -> list.visibleRows().size();
            case QUERY -> list.exactMatchCount() ? list.matchCount() : -1;
        };
    }

    private void countQueryForWarning(int token) {
        AllTheLogsClient.worker().countMatches(queries.filter().toSummaryQuery())
            .whenComplete((count, error) -> Minecraft.getInstance().execute(() -> {
                if (token != exportToken || error != null || count == null) return;
                if (exportProgress == null) exportTotal = count;
                if (count > LARGE_EXPORT) showLargeExportWarning();
                if (exporting != null && exportProgress == null) showIdleExportProgress();
            }));
    }

    private void reportExport(int token, ExportProgress progress) {
        latestExport.set(new ExportSnapshot(token, progress));
        if (exportProgressScheduled.compareAndSet(false, true)) {
            Minecraft.getInstance().execute(this::drainExportProgress);
        }
    }

    private void drainExportProgress() {
        exportProgressScheduled.set(false);
        ExportSnapshot snapshot = latestExport.getAndSet(null);
        if (snapshot == null || snapshot.token() != exportToken || !exportBusy) return;
        exportProgress = snapshot.progress();
        applyExportProgress(snapshot.progress());
    }

    private void applyExportProgress(ExportProgress progress) {
        if (exportFill == null || exportCounts == null || progress == null) return;
        int percent = progress.percent();
        exportFill.horizontalSizing(Sizing.fill(percent >= 100 ? 100 : Math.max(1, percent)));
        exportCounts.text(Component.translatable("allthelogs.export.progress",
            Long.toString(progress.written()), Long.toString(progress.total()), Integer.toString(percent)));
    }

    private void showIdleExportProgress() {
        if (exportProgress != null) {
            applyExportProgress(exportProgress);
            return;
        }
        if (exportFill == null || exportCounts == null) return;
        if (exportTotal >= 0) {
            applyExportProgress(new ExportProgress(0, exportTotal));
            return;
        }
        exportFill.horizontalSizing(Sizing.fill(1));
        exportCounts.text(Component.translatable("allthelogs.export.progress.loading"));
    }

    private void showLargeExportWarning() {
        exportLarge = true;
        if (exporting == null || exportLargeLabel != null) return;
        exportLargeLabel = UIComponents.label(Component.translatable("allthelogs.export.large"));
        exportLargeLabel.color(Color.ofRgb(0xE8D7A8));
        exportLargeLabel.maxWidth(Math.min(328, Math.max(160, this.width - 96)));
        if (!exporting.children().isEmpty() && exporting.children().getFirst() instanceof FlowLayout card) {
            card.child(exportLargeLabel);
        }
    }

    private void showExporting() {
        if (exporting != null || overlays == null) return;
        FlowLayout shade = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        shade.surface(Surface.flat(0x88000000));
        shade.padding(Insets.of(24));
        shade.horizontalAlignment(HorizontalAlignment.CENTER);
        shade.verticalAlignment(VerticalAlignment.CENTER);
        shade.positioning(Positioning.absolute(0, 0));
        shade.mouseDown().subscribe((mouse, doubled) -> true);
        shade.mouseScroll().subscribe((x, y, amount) -> true);
        int cardWidth = Math.min(360, Math.max(240, this.width - 48));
        FlowLayout card = UIContainers.verticalFlow(Sizing.fixed(cardWidth), Sizing.content());
        card.gap(8).padding(Insets.of(16)).surface(PanelSurfaces.card());
        card.child(UIComponents.label(Component.translatable("allthelogs.export.exporting")));
        exportCounts = UIComponents.label(Component.empty());
        exportCounts.color(Color.ofRgb(0xA0A0A0));
        exportCounts.maxWidth(Math.max(160, cardWidth - 32));
        card.child(exportCounts);
        FlowLayout track = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(10));
        track.surface(Surface.flat(0xFF1A1A1A).and(Surface.outline(0xFF3C3C3C)));
        exportFill = UIComponents.box(Sizing.fill(1), Sizing.fill());
        exportFill.fill(true).color(Color.ofRgb(0x7CB342));
        track.child(exportFill);
        card.child(track);
        shade.child(card);
        overlays.child(shade);
        exporting = shade;
        drainExportProgress();
        showIdleExportProgress();
        if (exportLarge) showLargeExportWarning();
    }

    private void hideExporting() {
        if (exporting != null && overlays != null && overlays.children().contains(exporting)) {
            overlays.removeChild(exporting);
        }
        exporting = null;
        exportCounts = null;
        exportFill = null;
        exportLargeLabel = null;
        exportLarge = false;
        exportTotal = -1;
        exportProgress = null;
    }

    private void showExportNotice(Component text, boolean error) {
        hideExportNotice();
        if (overlays == null) return;
        int token = ++noticeToken;
        FlowLayout notice = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        notice.padding(Insets.of(6, 6, 8, 8)).surface(PanelSurfaces.menu());
        int maxInner = Math.max(120, this.width - 32);
        LabelComponent label = UIComponents.label(text);
        label.maxWidth(maxInner);
        if (error) label.color(Color.ofRgb(0xE8A8A8));
        notice.child(label);
        var font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(text, maxInner);
        int textWidth = 0;
        for (FormattedCharSequence line : lines) textWidth = Math.max(textWidth, font.width(line));
        int width = Math.min(this.width - 8, Math.max(80, textWidth + 16));
        int height = Math.max(22, lines.size() * font.lineHeight + 12);
        ButtonComponent anchor = tools.button();
        int x = Math.max(4, anchor.x() + anchor.width() - width);
        int y = BrowserToolsMenu.dropdownTop(anchor.y(), anchor.height(), 4, height, this.height);
        notice.positioning(Positioning.absolute(x, y));
        notice.horizontalSizing(Sizing.fixed(width));
        overlays.child(notice);
        exportNotice = notice;
        int holdSeconds = error ? 4 : 8;
        CompletableFuture.delayedExecutor(holdSeconds, TimeUnit.SECONDS).execute(() ->
            Minecraft.getInstance().execute(() -> {
                if (token == noticeToken) hideExportNotice();
            }));
    }

    private void hideExportNotice() {
        if (exportNotice != null && overlays != null && overlays.children().contains(exportNotice)) {
            overlays.removeChild(exportNotice);
        }
        exportNotice = null;
    }

    private record ExportSnapshot(int token, ExportProgress progress) {
    }
}
