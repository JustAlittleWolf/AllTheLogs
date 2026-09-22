package me.wolfii.allthelogs.client.ui.screen;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.config.AllTheLogsConfig;
import me.wolfii.allthelogs.client.config.BrowserSession;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageSelection;
import me.wolfii.allthelogs.client.search.SearchDecorations;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transparent log browser: search bar, side filter panel, virtualised history, and a timeline of every hit.
 */
public final class LogBrowserScreen extends BaseOwoScreen<StackLayout> {
    private static final int SEARCH_DEBOUNCE_MS = 250;

    private final Screen parent;
    private final LogBrowserQueries queries;
    private MessageTimeline list;
    private TextBoxComponent search;
    private boolean syncingSearch;
    private ButtonComponent infoButton;
    private FilterOverlay filters;
    private StackLayout overlays;
    private DropdownComponent messageMenu;

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
        FlowLayout toolbar = buildToolbar();
        queries.attach(list, infoButton);
        content.child(list.verticalSizing(Sizing.expand()));
        content.child(toolbar);
        chrome.child(content);

        filters = new FilterOverlay(chrome, overlays, () -> this.width, () -> this.height,
            queries::filter, queries::versions, this::applyFilter);
        root.child(chrome);
        filters.restore();
        syncSearchBox();
    }

    @Override
    public void tick() {
        super.tick();
        clampSearchCursor();
    }

    @Override
    protected void init() {
        super.init();
        if (search != null && search.focusHandler() != null) {
            search.focusHandler().focus(search, UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
        if (list != null && queries.consumeReload()) {
            queries.reload();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
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
    public void onClose() {
        BrowserSession.remember(queries.filter());
        queries.rememberSessionLocation();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
        bar.gap(4).verticalAlignment(VerticalAlignment.CENTER);

        search = UIComponents.textBox(Sizing.expand(), SearchDecorations.wrap(queries.filter(), queries.filter().text()));
        search.setHint(Component.translatable("allthelogs.search.placeholder"));
        search.setMaxLength(256);
        search.addFormatter(this::formatSearch);
        search.onChanged().subscribe(this::onSearchChanged);
        refreshSearchColor();

        bar.child(search);

        bar.child(UIComponents.button(Component.translatable("allthelogs.filter"),
            button -> {
                filters.toggle();
                refreshSearchDecorations();
            }));
        if (!AllTheLogsConfig.get().hideImportButton()) {
            bar.child(UIComponents.button(Component.translatable("allthelogs.import.button"),
                button -> {
                    queries.markReload();
                    Minecraft.getInstance().gui.setScreen(new ImportScreen(this));
                }));
        }

        infoButton = UIComponents.button(Component.translatable("allthelogs.meta.marker"), button -> {
        });
        infoButton.tooltip(List.of(Component.translatable("allthelogs.meta.loading")));
        infoButton.horizontalSizing(Sizing.fixed(20));
        bar.child(infoButton);
        return bar;
    }

    private FormattedCharSequence formatSearch(String visible, int start) {
        return SearchDecorations.format(queries.filter(), visible, start);
    }

    private void onSearchChanged(String shown) {
        if (syncingSearch) return;
        SearchFilter current = queries.filter();
        String expected = SearchDecorations.wrap(current, current.text());
        if (!SearchDecorations.wraps(current)) {
            applySearchText(shown);
            return;
        }
        if (!shown.equals(expected)) {
            String inner = SearchDecorations.unwrap(current, shown);
            syncingSearch = true;
            try {
                search.setValue(SearchDecorations.wrap(current, inner));
                search.setCursorPosition(SearchDecorations.clampCursor(current, search.getValue(),
                    search.getCursorPosition()));
            } finally {
                syncingSearch = false;
            }
            applySearchText(inner);
            return;
        }
        applySearchText(SearchDecorations.unwrap(current, shown));
    }

    private void applySearchText(String text) {
        if (text.equals(queries.filter().text())) return;
        queries.updateFilter(queries.filter().withText(text));
        BrowserSession.remember(queries.filter());
        refreshSearchColor();
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

    private void applyFilter(SearchFilter next) {
        queries.setFilter(next);
        BrowserSession.remember(next);
        refreshSearchDecorations();
        if (filters != null) filters.syncFromFilter();
    }

    private void refreshSearchDecorations() {
        syncSearchBox();
        refreshSearchColor();
    }

    private void syncSearchBox() {
        if (search == null) return;
        String shown = SearchDecorations.wrap(queries.filter(), queries.filter().text());
        if (shown.equals(search.getValue())) {
            clampSearchCursor();
            return;
        }
        syncingSearch = true;
        try {
            search.setValue(shown);
        } finally {
            syncingSearch = false;
        }
        clampSearchCursor();
    }

    private void clampSearchCursor() {
        if (search == null || !SearchDecorations.wraps(queries.filter())) return;
        int clamped = SearchDecorations.clampCursor(queries.filter(), search.getValue(), search.getCursorPosition());
        if (clamped != search.getCursorPosition()) {
            search.setCursorPosition(clamped);
            search.setHighlightPos(clamped);
        }
    }

    private void refreshSearchColor() {
        if (search == null) return;
        search.setTextColor(queries.filter().invalidRegex() ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT);
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
}
