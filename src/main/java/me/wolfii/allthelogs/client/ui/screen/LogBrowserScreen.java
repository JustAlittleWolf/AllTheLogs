package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.config.AllTheLogsConfig;
import me.wolfii.allthelogs.client.config.FilterPersistence;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageSelection;
import me.wolfii.allthelogs.client.search.RegexHighlight;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
    private LabelComponent regexPrefix;
    private LabelComponent regexSuffix;
    private ButtonComponent infoButton;
    private FilterOverlay filters;
    private StackLayout overlays;

    public LogBrowserScreen() {
        this(null);
    }

    public LogBrowserScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.browser"));
        this.parent = parent;
        this.queries = new LogBrowserQueries(FilterPersistence.openingFilter());
        this.queries.restoreSessionLocation(
            AllTheLogsConfig.get().filterPersistence() != FilterPersistence.NOT_PERSISTED);
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.overlays = root;
        FlowLayout chrome = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fill());
        chrome.gap(6);

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
        FlowLayout toolbar = buildToolbar();
        queries.attach(list, infoButton);
        content.child(list.verticalSizing(Sizing.expand()));
        content.child(toolbar);
        chrome.child(content);

        filters = new FilterOverlay(chrome, overlays, () -> this.width, () -> this.height,
            queries::filter, queries::versions, this::applyFilter);
        root.child(chrome);
        filters.restore();
        refreshSearchDecorations();
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) {
            return;
        }
        this.extractPanorama(graphics, delta);
    }

    @Override
    public void onClose() {
        FilterPersistence.remember(queries.filter());
        queries.rememberSessionLocation(
            AllTheLogsConfig.get().filterPersistence() != FilterPersistence.NOT_PERSISTED);
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

        regexPrefix = UIComponents.label(Component.literal("/")).color(Color.ofRgb(Colors.REGEX_GROUP & 0xFFFFFF));
        regexSuffix = UIComponents.label(Component.literal("/")).color(Color.ofRgb(Colors.REGEX_GROUP & 0xFFFFFF));

        search = UIComponents.textBox(Sizing.expand(), queries.filter().text());
        search.setHint(Component.translatable("allthelogs.search.placeholder"));
        search.setMaxLength(256);
        search.addFormatter(this::formatSearch);
        search.onChanged().subscribe(this::onSearchChanged);
        refreshSearchColor();

        bar.child(regexPrefix);
        bar.child(search);
        bar.child(regexSuffix);

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
        infoButton.tooltip(List.of(Component.translatable("allthelogs.meta.unavailable")));
        infoButton.horizontalSizing(Sizing.fixed(20));
        bar.child(infoButton);
        return bar;
    }

    private FormattedCharSequence formatSearch(String visible, int start) {
        SearchFilter current = queries.filter();
        if (!current.regex()) {
            int color = current.invalidRegex() ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT;
            return FormattedCharSequence.forward(visible, Style.EMPTY.withColor(color & 0xFFFFFF));
        }
        return RegexHighlight.sequence(visible);
    }

    private void onSearchChanged(String text) {
        if (text.equals(queries.filter().text())) return;
        queries.updateFilter(queries.filter().withText(text));
        FilterPersistence.remember(queries.filter(), AllTheLogsConfig.get(), false);
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
        FilterPersistence.remember(next);
        refreshSearchDecorations();
        if (filters != null) filters.syncFromFilter();
    }

    private void refreshSearchDecorations() {
        refreshSearchColor();
        boolean regex = queries.filter().regex();
        if (regexPrefix != null) {
            regexPrefix.text(regex ? Component.literal("/") : Component.empty());
        }
        if (regexSuffix != null) {
            regexSuffix.text(regex ? Component.literal("/" + queries.filter().regexFlags()) : Component.empty());
        }
    }

    private void refreshSearchColor() {
        if (search == null) return;
        search.setTextColor(queries.filter().invalidRegex() ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT);
    }

    private void openMessageMenu(DisplayRow row, MessageSelection selection, List<DisplayRow> rows,
                                 double screenX, double screenY) {
        DropdownComponent.openContextMenu(this, overlays, StackLayout::child, screenX, screenY, menu -> {
            if (!selection.isEmpty()) {
                menu.button(Component.translatable("allthelogs.menu.copy_selection"), ignored ->
                    Minecraft.getInstance().keyboardHandler.setClipboard(selection.copy(rows)));
            }
            menu.button(Component.translatable("allthelogs.menu.copy_message"), ignored ->
                Minecraft.getInstance().keyboardHandler.setClipboard(row.message()));
            menu.button(Component.translatable("allthelogs.menu.filter_day"), ignored ->
                applyFilter(queries.filter().withDay(row.entry().timestamp().toLocalDate())));
        });
    }
}
