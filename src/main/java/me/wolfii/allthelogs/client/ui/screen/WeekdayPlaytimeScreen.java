package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.ui.widget.PlaytimeLegendWidget;
import me.wolfii.allthelogs.client.ui.widget.SeriesHighlight;
import me.wolfii.allthelogs.client.ui.widget.WeekdayChartWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Playtime by weekday beside a heat map of the hours of the day. Opened from the timeline screen, not the
 * hamburger menu. The period, version, and server filters apply to both charts.
 */
public final class WeekdayPlaytimeScreen extends BaseOwoScreen<StackLayout> {
    private final Screen parent;
    private final SeriesHighlight highlight = new SeriesHighlight();
    private StackLayout overlays;
    private WeekdayChartWidget chartWidget;
    private PlaytimeLegendWidget legend;
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
        FlowLayout page = PlaytimeScreenSupport.page();

        groupMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        versionMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);
        serverMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);

        groupButton = PlaytimeScreenSupport.optionButton(PlaytimeScreenSupport.groupLabel(PlaytimeSession.breakdown()),
            PlaytimeScreenSupport.groupLabels(), pressed -> openGroup());
        page.child(PlaytimeScreenSupport.optionsRow(List.of(groupButton),
            PlaytimeScreenSupport.button("allthelogs.playtime.timeline", pressed -> onClose())));

        chartWidget = new WeekdayChartWidget(highlight);
        legend = new PlaytimeLegendWidget(highlight);
        versionButton = PlaytimeScreenSupport.filterButton(PlaytimeScreenSupport.versionLabel(), pressed -> openVersions());
        serverButton = PlaytimeScreenSupport.filterButton(PlaytimeScreenSupport.serverLabel(), pressed -> openServers());
        page.child(PlaytimeScreenSupport.body(chartWidget, legend, List.of(
            dateField("allthelogs.filter.from", DateParser.format(PlaytimeSession.weekdayFrom()), DateParser::parse,
                PlaytimeSession::weekdayFrom),
            dateField("allthelogs.filter.until", DateParser.formatUntil(PlaytimeSession.weekdayUntil()),
                DateParser::parseUntil, PlaytimeSession::weekdayUntil),
            versionButton,
            serverButton)));
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

    /**
     * Label and text box for one end of the period. Text that is not a date yet is ignored; empty text
     * removes that bound.
     */
    private FlowLayout dateField(String key, String value, Function<String, Optional<LocalDateTime>> parse,
                                 Consumer<LocalDateTime> apply) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(32);
        box.setHint(Component.translatable("allthelogs.filter.date_hint"));
        box.onChanged().subscribe(text -> {
            if (!DateParser.isBlankOrValid(text)) return;
            apply.accept(parse.apply(text).orElse(null));
            rebuild();
        });
        row.child(box);
        return row;
    }

    private void load() {
        int request = ++loadToken;
        PlaytimeScreenSupport.load(this, request, () -> loadToken, this::status, this::rebuild);
    }

    private void rebuild() {
        chart = PlaytimeWeekday.build(PlaytimeSession.spans(), PlaytimeSession.filter(), PlaytimeSession.breakdown(),
            PlaytimeSession.weekdayFrom(), PlaytimeSession.weekdayUntil());
        if (chartWidget != null) chartWidget.chart(chart);
        if (legend != null) legend.entries(chart.series(), chart.legend());
        syncButtons();
        if (statusMessage == null) status(null);
    }

    private void status(@Nullable Component message) {
        statusMessage = message;
        PlaytimeScreenSupport.showTotal(legend, chartWidget == null ? null : chartWidget::placeholder,
            message, chart == null ? 0 : chart.totalMillis());
    }

    private void syncButtons() {
        if (groupButton != null) groupButton.setMessage(PlaytimeScreenSupport.groupLabel(PlaytimeSession.breakdown()));
        if (versionButton != null) versionButton.setMessage(PlaytimeScreenSupport.versionLabel());
        if (serverButton != null) serverButton.setMessage(PlaytimeScreenSupport.serverLabel());
    }

    private void openGroup() {
        boolean open = groupMenu.isOpen();
        closeMenus();
        if (open) return;
        groupMenu.show(groupButton, PlaytimeScreenSupport.groupChoices(), index -> {
            PlaytimeSession.breakdown(PlaytimeScreenSupport.GROUPS[index]);
            rebuild();
        });
    }

    private void openVersions() {
        PlaytimeScreenSupport.openVersions(versionMenu, versionButton, this::closeMenus, this::rebuild);
    }

    private void openServers() {
        PlaytimeScreenSupport.openServers(serverMenu, serverButton, this::closeMenus, this::rebuild);
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
}
