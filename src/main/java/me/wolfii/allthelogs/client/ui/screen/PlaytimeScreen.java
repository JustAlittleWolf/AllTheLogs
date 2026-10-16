package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import me.wolfii.allthelogs.client.config.BrowserSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeGrain;
import me.wolfii.allthelogs.client.playtime.PlaytimeMessageFilter;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeStyle;
import me.wolfii.allthelogs.client.playtime.PlaytimeWindow;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.widget.PlaytimeChartWidget;
import me.wolfii.allthelogs.client.ui.widget.PlaytimeLegendWidget;
import me.wolfii.allthelogs.client.ui.widget.SeriesHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Full playtime history. The hamburger menu opens this screen. Weekdays are a separate view reached from here.
 */
public final class PlaytimeScreen extends BaseOwoScreen<StackLayout> {
    private static final PlaytimeStyle[] STYLES = {
        PlaytimeStyle.BARS, PlaytimeStyle.CUMULATIVE, PlaytimeStyle.PERCENT
    };

    private final Screen parent;
    private boolean refreshOnShow = true;
    private final Consumer<SearchFilter> onFilterMessages;
    private StackLayout overlays;
    private final SeriesHighlight highlight = new SeriesHighlight();
    private PlaytimeChartWidget chartWidget;
    private PlaytimeLegendWidget legend;
    private PlaytimeScreenSupport.TotalRow totalRow;
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
        FlowLayout page = PlaytimeScreenSupport.page();

        grainMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        breakdownMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        styleMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        versionMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);
        serverMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);

        grainButton = PlaytimeScreenSupport.optionButton(grainLabel(PlaytimeSession.grain()),
            grains().stream().map(PlaytimeScreen::grainLabel).toList(), pressed -> openGrain());
        styleButton = PlaytimeScreenSupport.optionButton(styleLabel(PlaytimeSession.style()),
            Arrays.stream(STYLES).map(PlaytimeScreen::styleLabel).toList(), pressed -> openStyle());
        breakdownButton = PlaytimeScreenSupport.optionButton(PlaytimeScreenSupport.groupLabel(PlaytimeSession.breakdown()),
            PlaytimeScreenSupport.groupLabels(), pressed -> openBreakdown());
        grainMenu.opensOnHover(grainButton, this::openGrain);
        styleMenu.opensOnHover(styleButton, this::openStyle);
        breakdownMenu.opensOnHover(breakdownButton, this::openBreakdown);
        page.child(PlaytimeScreenSupport.optionsRow(List.of(grainButton, styleButton, breakdownButton),
            PlaytimeScreenSupport.button("allthelogs.playtime.weekdays", pressed -> openWeekdays())));

        chartWidget = new PlaytimeChartWidget(highlight);
        chartWidget.onZoom(this::zoom);
        chartWidget.onPan(this::pan);
        chartWidget.onReset(() -> {
            PlaytimeSession.window(null);
            rebuild();
        });
        chartWidget.onDismiss(this::closeContextMenu);
        chartWidget.onContext(this::openContextMenu);
        legend = new PlaytimeLegendWidget(highlight);
        totalRow = new PlaytimeScreenSupport.TotalRow();
        chartWidget.hoverBlocked(this::menusOpen);
        legend.hoverBlocked(this::menusOpen);
        versionButton = PlaytimeScreenSupport.filterButton(PlaytimeScreenSupport.versionLabel(), pressed -> openVersions());
        serverButton = PlaytimeScreenSupport.filterButton(PlaytimeScreenSupport.serverLabel(), pressed -> openServers());
        page.child(PlaytimeScreenSupport.body(chartWidget, totalRow, legend, List.of(versionButton, serverButton)));
        root.child(page);
        rebuild();
    }

    @Override
    protected void init() {
        super.init();
        if (refreshOnShow || !PlaytimeSession.loaded()) load();
        refreshOnShow = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (grainMenu != null) grainMenu.sync(mouseX, mouseY);
        if (styleMenu != null) styleMenu.sync(mouseX, mouseY);
        if (breakdownMenu != null) breakdownMenu.sync(mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) return;
        this.extractPanorama(graphics, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (versionMenu != null) versionMenu.closeOnClickOutside(event.x(), event.y());
        if (serverMenu != null) serverMenu.closeOnClickOutside(event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
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

    @Override
    public void removed() {
        super.removed();
        if (chartWidget != null) chartWidget.close();
        if (legend != null) legend.close();
    }

    private void load() {
        int request = ++loadToken;
        PlaytimeScreenSupport.load(this, request, () -> loadToken, this::status, this::rebuild);
    }

    private void rebuild() {
        chart = PlaytimeChartModel.build(PlaytimeSession.spans(), PlaytimeSession.filter(), PlaytimeSession.breakdown(),
            PlaytimeSession.style(), PlaytimeSession.grain(), PlaytimeSession.window(),
            chartWidget == null ? 0 : chartWidget.plotWidth());
        if (chartWidget != null) chartWidget.chart(chart);
        if (legend != null) legend.entries(chart.series(), chart.legend());
        syncButtons();
        if (statusMessage == null) status(null);
    }

    private void status(@Nullable Component message) {
        statusMessage = message;
        PlaytimeScreenSupport.showTotal(totalRow, chartWidget == null ? null : chartWidget::placeholder,
            message, chart == null ? 0 : chart.totalMillis());
    }

    private void syncButtons() {
        if (grainButton != null) grainButton.setMessage(grainLabel(PlaytimeSession.grain()));
        if (breakdownButton != null) breakdownButton.setMessage(PlaytimeScreenSupport.groupLabel(PlaytimeSession.breakdown()));
        if (styleButton != null) styleButton.setMessage(styleLabel(PlaytimeSession.style()));
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
        if (grainMenu.isOpen()) return;
        closeMenus();
        List<PlaytimeGrain> grains = grains();
        grainMenu.show(grainButton, grains.stream().map(PlaytimeScreen::grainName).toList(), index -> {
            PlaytimeSession.grain(grains.get(index));
            rebuild();
        });
    }

    private void openBreakdown() {
        if (breakdownMenu.isOpen()) return;
        closeMenus();
        breakdownMenu.show(breakdownButton, PlaytimeScreenSupport.groupChoices(), index -> {
            PlaytimeSession.breakdown(PlaytimeScreenSupport.GROUPS[index]);
            rebuild();
        });
    }

    private void openStyle() {
        if (styleMenu.isOpen()) return;
        closeMenus();
        styleMenu.show(styleButton, Arrays.stream(STYLES).map(PlaytimeScreen::styleName).toList(), index -> {
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
        if (chartWidget != null) chartWidget.unpin();
    }

    private boolean menusOpen() {
        return (grainMenu != null && grainMenu.isOpen())
            || (breakdownMenu != null && breakdownMenu.isOpen())
            || (styleMenu != null && styleMenu.isOpen())
            || (versionMenu != null && versionMenu.isOpen())
            || (serverMenu != null && serverMenu.isOpen())
            || contextMenuOpen();
    }

    /**
     * Whether the right-click menu is still shown. It can close itself, e.g. on a click elsewhere, so a stale
     * reference is cleared here, which also releases the selected bucket.
     */
    private boolean contextMenuOpen() {
        if (contextMenu != null && (overlays == null || !overlays.children().contains(contextMenu))) closeContextMenu();
        return contextMenu != null;
    }

    private void closeMenus() {
        if (grainMenu != null) grainMenu.close();
        if (breakdownMenu != null) breakdownMenu.close();
        if (styleMenu != null) styleMenu.close();
        if (versionMenu != null) versionMenu.close();
        if (serverMenu != null) serverMenu.close();
    }

    /**
     * Bar widths on offer. Years only appear when the history covers more than one calendar year.
     */
    private static List<PlaytimeGrain> grains() {
        List<PlaytimeGrain> grains = new ArrayList<>(List.of(
            PlaytimeGrain.AUTO, PlaytimeGrain.DAYS, PlaytimeGrain.WEEKS, PlaytimeGrain.MONTHS));
        if (PlaytimeChartModel.spansSeveralYears(PlaytimeSession.spans()) || PlaytimeSession.grain() == PlaytimeGrain.YEARS) {
            grains.add(PlaytimeGrain.YEARS);
        }
        return grains;
    }

    private static Component grainName(PlaytimeGrain grain) {
        return Component.translatable(switch (grain) {
            case AUTO -> "allthelogs.playtime.grain.auto";
            case DAYS -> "allthelogs.playtime.grain.days";
            case WEEKS -> "allthelogs.playtime.grain.weeks";
            case MONTHS -> "allthelogs.playtime.grain.months";
            case YEARS -> "allthelogs.playtime.grain.years";
        });
    }

    private static Component grainLabel(PlaytimeGrain grain) {
        return Component.translatable("allthelogs.playtime.grain", grainName(grain));
    }

    private static Component styleName(PlaytimeStyle style) {
        return Component.translatable(switch (style) {
            case BARS -> "allthelogs.playtime.style.bars";
            case CUMULATIVE -> "allthelogs.playtime.style.cumulative";
            case PERCENT -> "allthelogs.playtime.style.percent";
        });
    }

    private static Component styleLabel(PlaytimeStyle style) {
        return Component.translatable("allthelogs.playtime.style", styleName(style));
    }
}
