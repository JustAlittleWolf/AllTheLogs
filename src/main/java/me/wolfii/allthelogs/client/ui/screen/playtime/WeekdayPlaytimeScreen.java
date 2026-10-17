package me.wolfii.allthelogs.client.ui.screen.playtime;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.ui.popup.ChecklistMenu;
import me.wolfii.allthelogs.client.ui.popup.OptionMenu;
import me.wolfii.allthelogs.client.ui.widget.DateField;
import me.wolfii.allthelogs.client.ui.widget.playtime.PlaytimeLegendWidget;
import me.wolfii.allthelogs.client.ui.widget.playtime.SeriesHighlight;
import me.wolfii.allthelogs.client.ui.widget.playtime.WeekdayChartWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Arrays;
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
    private PlaytimeScreenSupport.TotalRow totalRow;
    private ButtonComponent groupButton;
    private ButtonComponent detailButton;
    private ButtonComponent versionButton;
    private ButtonComponent serverButton;
    private OptionMenu groupMenu;
    private OptionMenu detailMenu;
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
        detailMenu = new OptionMenu(overlays, () -> this.width, () -> this.height);
        versionMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);
        serverMenu = new ChecklistMenu(overlays, () -> this.width, () -> this.height);

        groupButton = PlaytimeScreenSupport.optionButton(PlaytimeScreenSupport.groupLabel(PlaytimeSession.breakdown()),
            PlaytimeScreenSupport.groupLabels(), pressed -> openGroup());
        groupMenu.opensOnHover(groupButton, this::openGroup);
        detailButton = PlaytimeScreenSupport.optionButton(detailLabel(PlaytimeSession.heatSlotMinutes()),
            Arrays.stream(PlaytimeWeekday.SLOT_MINUTES).mapToObj(WeekdayPlaytimeScreen::detailLabel).toList(),
            pressed -> openDetail());
        detailMenu.opensOnHover(detailButton, this::openDetail);
        page.child(PlaytimeScreenSupport.optionsRow(List.of(groupButton, detailButton),
            PlaytimeScreenSupport.button("allthelogs.playtime.timeline", pressed -> onClose())));

        chartWidget = new WeekdayChartWidget(highlight);
        legend = new PlaytimeLegendWidget(highlight);
        totalRow = new PlaytimeScreenSupport.TotalRow();
        chartWidget.hoverBlocked(this::menusOpen);
        legend.hoverBlocked(this::menusOpen);
        versionButton = PlaytimeScreenSupport.filterButton(PlaytimeScreenSupport.versionLabel(), pressed -> openVersions());
        serverButton = PlaytimeScreenSupport.filterButton(PlaytimeScreenSupport.serverLabel(), pressed -> openServers());
        page.child(PlaytimeScreenSupport.body(chartWidget, totalRow, legend, List.of(
            dateField("allthelogs.filter.from", DateParser.format(PlaytimeSession.weekdayFrom()), DateParser::parse,
                PlaytimeSession::weekdayFrom),
            dateField("allthelogs.filter.until", DateParser.formatUntil(PlaytimeSession.weekdayUntil()),
                DateParser::parseUntil, PlaytimeSession::weekdayUntil),
            versionButton,
            serverButton)));
        root.child(page);
        rebuild();
    }

    /**
     * Uses the playtime the timeline already loaded, so switching between the two screens does not query the
     * store again.
     */
    @Override
    protected void init() {
        super.init();
        if (!PlaytimeSession.loaded()) load();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (groupMenu != null) groupMenu.sync(mouseX, mouseY);
        if (detailMenu != null) detailMenu.sync(mouseX, mouseY);
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

    /**
     * One end of the period, like the message filters.
     */
    private FlowLayout dateField(String key, String value, Function<String, Optional<LocalDateTime>> parse,
                                 Consumer<LocalDateTime> apply) {
        return new DateField(key, value, parse, bound -> {
            apply.accept(bound);
            rebuild();
        }).row();
    }

    private void load() {
        int request = ++loadToken;
        PlaytimeScreenSupport.load(this, request, () -> loadToken, this::status, this::rebuild);
    }

    private void rebuild() {
        chart = PlaytimeWeekday.build(PlaytimeSession.spans(), PlaytimeSession.filter(), PlaytimeSession.breakdown(),
            PlaytimeSession.weekdayFrom(), PlaytimeSession.weekdayUntil(), PlaytimeSession.heatSlotMinutes(),
            PlaytimeSession.majorVersions());
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
        if (groupButton != null) groupButton.setMessage(PlaytimeScreenSupport.groupLabel(PlaytimeSession.breakdown()));
        if (detailButton != null) detailButton.setMessage(detailLabel(PlaytimeSession.heatSlotMinutes()));
        if (versionButton != null) versionButton.setMessage(PlaytimeScreenSupport.versionLabel());
        if (serverButton != null) serverButton.setMessage(PlaytimeScreenSupport.serverLabel());
    }

    private void openGroup() {
        PlaytimeScreenSupport.openBreakdown(groupMenu, groupButton, this::closeMenus, this::rebuild);
    }

    private void openDetail() {
        if (detailMenu.isOpen()) return;
        closeMenus();
        detailMenu.show(detailButton, Arrays.stream(PlaytimeWeekday.SLOT_MINUTES)
            .mapToObj(WeekdayPlaytimeScreen::slotName).toList(), index -> {
            PlaytimeSession.heatSlotMinutes(PlaytimeWeekday.SLOT_MINUTES[index]);
            rebuild();
        });
    }

    private static Component slotName(int minutes) {
        return minutes % 60 == 0
            ? Component.translatable("allthelogs.playtime.heat.hours", minutes / 60)
            : Component.translatable("allthelogs.playtime.heat.minutes", minutes);
    }

    private static Component detailLabel(int minutes) {
        return Component.translatable("allthelogs.playtime.heat.detail", slotName(minutes));
    }

    private void openVersions() {
        PlaytimeScreenSupport.openVersions(versionMenu, versionButton, this::closeMenus, this::rebuild);
    }

    private void openServers() {
        PlaytimeScreenSupport.openServers(serverMenu, serverButton, this::closeMenus, this::rebuild);
    }

    private boolean menusOpen() {
        return (groupMenu != null && groupMenu.isOpen())
            || (detailMenu != null && detailMenu.isOpen())
            || (versionMenu != null && versionMenu.isOpen())
            || (serverMenu != null && serverMenu.isOpen());
    }

    private void closeMenus() {
        if (groupMenu != null) groupMenu.close();
        if (detailMenu != null) detailMenu.close();
        if (versionMenu != null) versionMenu.close();
        if (serverMenu != null) serverMenu.close();
    }
}
