package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transparent log browser: search bar, filter overlay, virtualised history, and a timeline of every hit.
 */
public final class LogBrowserScreen extends BaseOwoScreen<StackLayout> {
    private static final int SEARCH_DEBOUNCE_MS = 100;

    private final Screen parent;
    private final LogBrowserQueries queries = new LogBrowserQueries();
    private MessageTimeline list;
    private TextBoxComponent search;
    private ButtonComponent infoButton;
    private FilterOverlay filters;
    private StackLayout overlays;

    public LogBrowserScreen() {
        this(null);
    }

    public LogBrowserScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.browser"));
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.overlays = root;
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        content.gap(6);
        content.allowOverflow(true);
        content.surface(Surface.blur(5, 10).and(PanelSurfaces.overlay()))
            .padding(Insets.of(8))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        list = new MessageTimeline();
        FlowLayout toolbar = buildToolbar();
        queries.attach(list, infoButton);
        filters = new FilterOverlay(overlays, () -> this.width, () -> this.height,
            queries::filter, queries::versions, this::applyFilter);
        content.child(list.verticalSizing(Sizing.expand()));
        content.child(toolbar);
        root.child(content);
    }

    @Override
    protected void init() {
        super.init();
        if (search != null && search.focusHandler() != null) {
            search.focusHandler().focus(search, UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
        if (list != null && queries.consumeReload()) {
            queries.reload(true);
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

        search = UIComponents.textBox(Sizing.expand(), queries.filter().text());
        search.setHint(Component.translatable("allthelogs.search.placeholder"));
        search.setMaxLength(256);
        search.onChanged().subscribe(this::onSearchChanged);
        bar.child(search);

        bar.child(UIComponents.button(Component.translatable("allthelogs.filter"),
            button -> filters.toggle(button)));
        bar.child(UIComponents.button(Component.translatable("allthelogs.import.button"),
            button -> {
                queries.markReload();
                Minecraft.getInstance().gui.setScreen(new ImportScreen(this));
            }));

        infoButton = UIComponents.button(Component.translatable("allthelogs.meta.marker"), button -> {
        });
        infoButton.tooltip(List.of(
            Component.translatable("allthelogs.meta.hint"),
            Component.translatable("allthelogs.meta.unavailable")));
        infoButton.horizontalSizing(Sizing.fixed(20));
        bar.child(infoButton);
        return bar;
    }

    private void onSearchChanged(String text) {
        if (text.equals(queries.filter().text())) return;
        queries.updateFilter(queries.filter().withText(text));
        int generation = queries.bumpGeneration();
        CompletableFuture.delayedExecutor(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation == queries.currentGeneration()) {
                Minecraft.getInstance().execute(() -> queries.reload(true));
            }
        });
    }

    private void applyFilter(SearchFilter next) {
        queries.setFilter(next);
        if (filters != null) filters.syncSortButtons();
    }
}
