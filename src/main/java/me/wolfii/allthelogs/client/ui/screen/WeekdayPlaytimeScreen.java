package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
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
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.playtime.PlaytimeBreakdown;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.WeekdayChartWidget;
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
 * Monday-to-Sunday playtime. Opened from the timeline screen, not the hamburger menu.
 */
public final class WeekdayPlaytimeScreen extends BaseOwoScreen<StackLayout> {
    private static final PlaytimeBreakdown[] GROUPS = {
        PlaytimeBreakdown.VERSION, PlaytimeBreakdown.SERVER, PlaytimeBreakdown.COMBINED
    };
    private static final String[] DAY_KEYS = {
        "allthelogs.playtime.day.monday",
        "allthelogs.playtime.day.tuesday",
        "allthelogs.playtime.day.wednesday",
        "allthelogs.playtime.day.thursday",
        "allthelogs.playtime.day.friday",
        "allthelogs.playtime.day.saturday",
        "allthelogs.playtime.day.sunday"
    };

    private final Screen parent;
    private StackLayout overlays;
    private WeekdayChartWidget chartWidget;
    private LabelComponent totalLabel;
    private LabelComponent mostLabel;
    private LabelComponent leastLabel;
    private FlowLayout legend;
    private ButtonComponent groupButton;
    private ButtonComponent versionButton;
    private ButtonComponent serverButton;
    private OptionMenu groupMenu;
    private ChecklistMenu versionMenu;
    private ChecklistMenu serverMenu;
    private PlaytimeWeekday.Result chart;
    private Component statusMessage;
    private int loadToken;

    public WeekdayPlaytimeScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.playtime.weekdays"));
        this.parent = parent;
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
        header.child(button("allthelogs.playtime.timeline", pressed -> onClose()));
        page.child(header);

        FlowLayout extremes = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        extremes.gap(12).verticalAlignment(VerticalAlignment.CENTER);
        mostLabel = UIComponents.label(Component.empty());
        leastLabel = UIComponents.label(Component.empty());
        extremes.child(mostLabel);
        extremes.child(leastLabel);
        page.child(extremes);

        groupMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        versionMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);
        serverMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);

        FlowLayout controls = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        controls.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        groupButton = button(groupLabel(), pressed -> openGroup());
        versionButton = button(versionLabel(), pressed -> openVersions());
        serverButton = button(serverLabel(), pressed -> openServers());
        controls.child(groupButton);
        controls.child(versionButton);
        controls.child(serverButton);
        page.child(controls);

        chartWidget = new WeekdayChartWidget();
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
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) {
            status(Component.translatable("allthelogs.playtime.unavailable"));
            return;
        }
        if (!PlaytimeSession.loaded()) status(Component.translatable("allthelogs.playtime.loading"));
        worker.playtime().whenComplete((spans, error) -> Minecraft.getInstance().execute(() -> {
            if (request != loadToken || Minecraft.getInstance().gui.screen() != this) return;
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("Could not read playtime", error);
                status(Component.translatable("allthelogs.status.error"));
                return;
            }
            PlaytimeSession.spans(spans);
            status(null);
            rebuild();
        }));
    }

    private void rebuild() {
        chart = PlaytimeWeekday.build(PlaytimeSession.spans(), PlaytimeSession.filter(), PlaytimeSession.breakdown());
        if (chartWidget != null) chartWidget.chart(chart);
        if (legend != null) PlaytimeLegend.fill(legend, chart.series(), chart.legend());
        if (mostLabel != null && leastLabel != null) {
            if (chart.isEmpty()) {
                mostLabel.text(Component.empty());
                leastLabel.text(Component.empty());
            } else {
                mostLabel.text(extreme("allthelogs.playtime.most", chart.mostDay()));
                leastLabel.text(chart.mostDay() == chart.leastDay()
                    ? Component.empty()
                    : extreme("allthelogs.playtime.least", chart.leastDay()));
            }
        }
        syncButtons();
        if (statusMessage == null) status(null);
    }

    private Component extreme(String key, int day) {
        String name = Component.translatable(DAY_KEYS[day]).getString();
        return Component.translatable(key, name, PlaytimeDuration.format(chart.bars().get(day).totalMillis()));
    }

    private void status(@Nullable Component message) {
        statusMessage = message;
        if (totalLabel == null) return;
        if (message != null) {
            totalLabel.text(message);
            if (chartWidget != null) chartWidget.placeholder(message);
            return;
        }
        long total = chart == null ? 0 : chart.totalMillis();
        totalLabel.text(Component.translatable("allthelogs.playtime.total", PlaytimeDuration.format(total)));
        if (chartWidget != null) chartWidget.placeholder(Component.translatable("allthelogs.playtime.empty"));
    }

    private void syncButtons() {
        if (groupButton != null) groupButton.setMessage(groupLabel());
        if (versionButton != null) versionButton.setMessage(versionLabel());
        if (serverButton != null) serverButton.setMessage(serverLabel());
    }

    private void openGroup() {
        boolean open = groupMenu.isOpen();
        closeMenus();
        if (open) return;
        groupMenu.show(groupButton, List.of(
            Component.translatable("allthelogs.playtime.breakdown.version"),
            Component.translatable("allthelogs.playtime.breakdown.server"),
            Component.translatable("allthelogs.playtime.breakdown.combined")
        ), index -> {
            PlaytimeSession.breakdown(GROUPS[index]);
            rebuild();
        });
    }

    private void openVersions() {
        boolean open = versionMenu.isOpen();
        closeMenus();
        if (open) return;
        List<String> values = PlaytimeChartModel.versionsIn(PlaytimeSession.spans());
        versionMenu.show(versionButton, values, PlaytimeSession.filter().versions(), PlaytimeLegend::named,
            (value, selected) -> {
                PlaytimeSession.filter(PlaytimeSession.filter().toggleVersion(value, selected, values));
                rebuild();
            }, () -> {
                PlaytimeSession.filter(PlaytimeSession.filter().withVersions(null));
                versionMenu.close();
                rebuild();
            });
    }

    private void openServers() {
        boolean open = serverMenu.isOpen();
        closeMenus();
        if (open) return;
        List<String> values = PlaytimeChartModel.serversIn(PlaytimeSession.spans());
        serverMenu.show(serverButton, values, PlaytimeSession.filter().servers(), PlaytimeLegend::named,
            (value, selected) -> {
                PlaytimeSession.filter(PlaytimeSession.filter().toggleServer(value, selected, values));
                rebuild();
            }, () -> {
                PlaytimeSession.filter(PlaytimeSession.filter().withServers(null));
                serverMenu.close();
                rebuild();
            });
    }

    private boolean menusOpen() {
        return (groupMenu != null && groupMenu.isOpen())
            || (versionMenu != null && versionMenu.isOpen())
            || (serverMenu != null && serverMenu.isOpen());
    }

    private void closeMenus() {
        if (groupMenu != null) groupMenu.close();
        if (versionMenu != null) versionMenu.close();
        if (serverMenu != null) serverMenu.close();
    }

    private Component groupLabel() {
        String key = switch (PlaytimeSession.breakdown()) {
            case VERSION -> "allthelogs.playtime.breakdown.version";
            case SERVER -> "allthelogs.playtime.breakdown.server";
            case COMBINED -> "allthelogs.playtime.breakdown.combined";
        };
        return Component.translatable("allthelogs.playtime.weekday.group", Component.translatable(key));
    }

    private Component versionLabel() {
        return PlaytimeLegend.selection("allthelogs.playtime.versions", PlaytimeSession.filter().versions(),
            PlaytimeChartModel.versionsIn(PlaytimeSession.spans()));
    }

    private Component serverLabel() {
        return PlaytimeLegend.selection("allthelogs.playtime.servers", PlaytimeSession.filter().servers(),
            PlaytimeChartModel.serversIn(PlaytimeSession.spans()));
    }

    private static ButtonComponent button(String key, Consumer<ButtonComponent> onPress) {
        return button(Component.translatable(key), onPress);
    }

    private static ButtonComponent button(Component label, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(label, onPress::accept);
        button.horizontalSizing(Sizing.content());
        return button;
    }
}
