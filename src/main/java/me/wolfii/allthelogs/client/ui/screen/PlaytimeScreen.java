package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.config.BrowserSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeBreakdown;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeGrain;
import me.wolfii.allthelogs.client.playtime.PlaytimeMessageFilter;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeStyle;
import me.wolfii.allthelogs.client.playtime.PlaytimeWindow;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.PlaytimeChartWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Full playtime history. The hamburger menu opens this screen. Weekdays are a separate view reached from here.
 */
public final class PlaytimeScreen extends BaseOwoScreen<StackLayout> {
    private static final PlaytimeGrain[] GRAINS = {
        PlaytimeGrain.AUTO, PlaytimeGrain.DAYS, PlaytimeGrain.WEEKS, PlaytimeGrain.MONTHS
    };
    private static final PlaytimeBreakdown[] GROUPS = {
        PlaytimeBreakdown.VERSION, PlaytimeBreakdown.SERVER, PlaytimeBreakdown.COMBINED
    };
    private static final PlaytimeStyle[] STYLES = {
        PlaytimeStyle.BARS, PlaytimeStyle.CUMULATIVE, PlaytimeStyle.PERCENT
    };

    private final Screen parent;
    private final Consumer<SearchFilter> onFilterMessages;
    private StackLayout overlays;
    private PlaytimeChartWidget chartWidget;
    private LabelComponent totalLabel;
    private FlowLayout legend;
    private ButtonComponent grainButton;
    private ButtonComponent breakdownButton;
    private ButtonComponent styleButton;
    private ButtonComponent versionButton;
    private ButtonComponent serverButton;
    private OptionMenu grainMenu;
    private OptionMenu breakdownMenu;
    private OptionMenu styleMenu;
    private ChecklistMenu versionMenu;
    private ChecklistMenu serverMenu;
    private DropdownComponent contextMenu;
    private PlaytimeChartModel.Result chart;
    private Component statusMessage;
    private int loadToken;

    public PlaytimeScreen(@Nullable Screen parent, Consumer<SearchFilter> onFilterMessages) {
        super(Component.translatable("allthelogs.screen.playtime"));
        this.parent = parent;
        this.onFilterMessages = onFilterMessages;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.overlays = root;
        root.allowOverflow(true);
        FlowLayout page = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        page.gap(6).padding(Insets.of(8));
        page.surface(Surface.blur(5, 10).and(PanelSurfaces.overlay()));
        page.horizontalAlignment(HorizontalAlignment.LEFT);
        page.verticalAlignment(VerticalAlignment.TOP);

        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        header.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        totalLabel = UIComponents.label(Component.translatable("allthelogs.playtime.loading"));
        totalLabel.horizontalSizing(Sizing.expand());
        header.child(totalLabel);
        header.child(PlaytimeScreenSupport.button("allthelogs.playtime.weekdays", pressed -> openWeekdays()));
        page.child(header);

        grainMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        breakdownMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        styleMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        versionMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);
        serverMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);

        FlowLayout controls = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        controls.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        grainButton = PlaytimeScreenSupport.button(grainLabel(), pressed -> openGrain());
        breakdownButton = PlaytimeScreenSupport.button(breakdownLabel(), pressed -> openBreakdown());
        styleButton = PlaytimeScreenSupport.button(styleLabel(), pressed -> openStyle());
        versionButton = PlaytimeScreenSupport.button(PlaytimeScreenSupport.versionLabel(), pressed -> openVersions());
        serverButton = PlaytimeScreenSupport.button(PlaytimeScreenSupport.serverLabel(), pressed -> openServers());
        controls.child(grainButton);
        controls.child(breakdownButton);
        controls.child(styleButton);
        controls.child(versionButton);
        controls.child(serverButton);
        page.child(controls);

        chartWidget = new PlaytimeChartWidget();
        chartWidget.onZoom(this::zoom);
        chartWidget.onPan(this::pan);
        chartWidget.onReset(() -> {
            PlaytimeSession.window(null);
            rebuild();
        });
        chartWidget.onDismiss(this::closeContextMenu);
        chartWidget.onContext(this::openContextMenu);
        page.child(chartWidget.verticalSizing(Sizing.expand()));

        legend = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        legend.gap(10).verticalAlignment(VerticalAlignment.CENTER);
        ScrollContainer<FlowLayout> legendScroll = UIContainers.horizontalScroll(Sizing.fill(), Sizing.fixed(18), legend);
        legendScroll.scrollbar(OverflowScrollbar.vanillaFlat());
        page.child(legendScroll);
        root.child(page);
        rebuild();
    }

    @Override
    protected void init() {
        super.init();
        load();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) return;
        this.extractPanorama(graphics, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && menusOpen()) {
            closeMenus();
            closeContextMenu();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void load() {
        int request = ++loadToken;
        PlaytimeScreenSupport.load(this, request, () -> loadToken, this::status, this::rebuild);
    }

    private void rebuild() {
        chart = PlaytimeChartModel.build(PlaytimeSession.spans(), PlaytimeSession.filter(), PlaytimeSession.breakdown(),
            PlaytimeSession.style(), PlaytimeSession.grain(), PlaytimeSession.window());
        if (chartWidget != null) chartWidget.chart(chart);
        if (legend != null) PlaytimeLegend.fill(legend, chart.series(), chart.legend());
        syncButtons();
        if (statusMessage == null) status(null);
    }

    private void status(@Nullable Component message) {
        statusMessage = message;
        PlaytimeScreenSupport.showTotal(totalLabel, chartWidget == null ? null : chartWidget::placeholder,
            message, chart == null ? 0 : chart.totalMillis());
    }

    private void syncButtons() {
        if (grainButton != null) grainButton.setMessage(grainLabel());
        if (breakdownButton != null) breakdownButton.setMessage(breakdownLabel());
        if (styleButton != null) styleButton.setMessage(styleLabel());
        if (versionButton != null) versionButton.setMessage(PlaytimeScreenSupport.versionLabel());
        if (serverButton != null) serverButton.setMessage(PlaytimeScreenSupport.serverLabel());
    }

    private void zoom(double fraction, double steps) {
        if (chart == null || chart.isEmpty()) return;
        PlaytimeWindow domain = new PlaytimeWindow(chart.domainStart(), chart.domainEnd());
        PlaytimeWindow view = new PlaytimeWindow(chart.viewStart(), chart.viewEnd());
        PlaytimeSession.window(PlaytimeWindow.zoom(domain, view, fraction, steps));
        rebuild();
    }

    private void pan(double fractionDelta) {
        if (chart == null || chart.isEmpty()) return;
        PlaytimeWindow domain = new PlaytimeWindow(chart.domainStart(), chart.domainEnd());
        PlaytimeWindow view = new PlaytimeWindow(chart.viewStart(), chart.viewEnd());
        PlaytimeSession.window(PlaytimeWindow.pan(domain, view, fractionDelta));
        rebuild();
    }

    private void openWeekdays() {
        closeMenus();
        Minecraft.getInstance().gui.setScreen(new WeekdayPlaytimeScreen(this));
    }

    private void openGrain() {
        boolean open = grainMenu.isOpen();
        closeMenus();
        if (open) return;
        grainMenu.show(grainButton, List.of(
            Component.translatable("options.guiScale.auto"),
            Component.translatable("allthelogs.playtime.grain.days"),
            Component.translatable("allthelogs.playtime.grain.weeks"),
            Component.translatable("allthelogs.playtime.grain.months")
        ), index -> {
            PlaytimeSession.grain(GRAINS[index]);
            rebuild();
        });
    }

    private void openBreakdown() {
        boolean open = breakdownMenu.isOpen();
        closeMenus();
        if (open) return;
        breakdownMenu.show(breakdownButton, List.of(
            Component.translatable("allthelogs.playtime.breakdown.version"),
            Component.translatable("allthelogs.playtime.breakdown.server"),
            Component.translatable("allthelogs.playtime.breakdown.combined")
        ), index -> {
            PlaytimeSession.breakdown(GROUPS[index]);
            rebuild();
        });
    }

    private void openStyle() {
        boolean open = styleMenu.isOpen();
        closeMenus();
        if (open) return;
        styleMenu.show(styleButton, List.of(
            Component.translatable("allthelogs.playtime.style.bars"),
            Component.translatable("allthelogs.playtime.style.cumulative"),
            Component.translatable("allthelogs.playtime.style.percent")
        ), index -> {
            PlaytimeSession.style(STYLES[index]);
            rebuild();
        });
    }

    private void openVersions() {
        PlaytimeScreenSupport.openVersions(versionMenu, versionButton, this::closeMenus, this::rebuild);
    }

    private void openServers() {
        PlaytimeScreenSupport.openServers(serverMenu, serverButton, this::closeMenus, this::rebuild);
    }

    private void openContextMenu(PlaytimeChartModel.Hit hit, double screenX, double screenY) {
        closeMenus();
        closeContextMenu();
        if (chart == null || hit.column() < 0 || hit.column() >= chart.columns().size()) return;
        PlaytimeChartModel.Column column = chart.columns().get(hit.column());
        PlaytimeSeries series = hit.series() >= 0 && hit.series() < chart.series().size()
            ? chart.series().get(hit.series()) : null;
        contextMenu = DropdownComponent.openContextMenu(this, overlays, StackLayout::child, screenX, screenY, menu -> {
            menu.button(Component.translatable("allthelogs.playtime.menu.range"), ignored -> {
                closeContextMenu();
                applyMessages(PlaytimeMessageFilter.period(BrowserSession.openingFilter(), column.start(), column.end()));
            });
            if (series != null && offersCategory(series)) {
                menu.button(Component.translatable(categoryKey(series)), ignored -> {
                    closeContextMenu();
                    applyMessages(PlaytimeMessageFilter.category(BrowserSession.openingFilter(), column.start(),
                        column.end(), chart.breakdown(), series));
                });
            }
        });
    }

    private boolean offersCategory(PlaytimeSeries series) {
        return switch (chart.breakdown()) {
            case VERSION -> series.canFilterVersion();
            case SERVER -> series.canFilterServer();
            case COMBINED -> series.canFilterVersion() || series.canFilterServer();
        };
    }

    private String categoryKey(PlaytimeSeries series) {
        return switch (chart.breakdown()) {
            case VERSION -> "allthelogs.playtime.menu.version";
            case SERVER -> "allthelogs.playtime.menu.server";
            case COMBINED -> series.canFilterVersion() && series.canFilterServer()
                ? "allthelogs.playtime.menu.both"
                : series.canFilterVersion() ? "allthelogs.playtime.menu.version" : "allthelogs.playtime.menu.server";
        };
    }

    private void applyMessages(SearchFilter filter) {
        if (onFilterMessages != null) onFilterMessages.accept(filter);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void closeContextMenu() {
        if (contextMenu != null && overlays != null && overlays.children().contains(contextMenu)) {
            overlays.removeChild(contextMenu);
        }
        contextMenu = null;
    }

    private boolean menusOpen() {
        return (grainMenu != null && grainMenu.isOpen())
            || (breakdownMenu != null && breakdownMenu.isOpen())
            || (styleMenu != null && styleMenu.isOpen())
            || (versionMenu != null && versionMenu.isOpen())
            || (serverMenu != null && serverMenu.isOpen())
            || contextMenu != null;
    }

    private void closeMenus() {
        if (grainMenu != null) grainMenu.close();
        if (breakdownMenu != null) breakdownMenu.close();
        if (styleMenu != null) styleMenu.close();
        if (versionMenu != null) versionMenu.close();
        if (serverMenu != null) serverMenu.close();
    }

    private Component grainLabel() {
        String key = switch (PlaytimeSession.grain()) {
            case AUTO -> "options.guiScale.auto";
            case DAYS -> "allthelogs.playtime.grain.days";
            case WEEKS -> "allthelogs.playtime.grain.weeks";
            case MONTHS -> "allthelogs.playtime.grain.months";
        };
        return Component.translatable("allthelogs.playtime.grain", Component.translatable(key));
    }

    private Component breakdownLabel() {
        String key = switch (PlaytimeSession.breakdown()) {
            case VERSION -> "allthelogs.playtime.breakdown.version";
            case SERVER -> "allthelogs.playtime.breakdown.server";
            case COMBINED -> "allthelogs.playtime.breakdown.combined";
        };
        return Component.translatable("allthelogs.playtime.breakdown", Component.translatable(key));
    }

    private Component styleLabel() {
        String key = switch (PlaytimeSession.style()) {
            case BARS -> "allthelogs.playtime.style.bars";
            case CUMULATIVE -> "allthelogs.playtime.style.cumulative";
            case PERCENT -> "allthelogs.playtime.style.percent";
        };
        return Component.translatable("allthelogs.playtime.style", Component.translatable(key));
    }

}
