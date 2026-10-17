package me.wolfii.allthelogs.client.ui.screen.playtime;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.playtime.PlaytimeBreakdown;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import me.wolfii.allthelogs.client.search.ActiveFilters;
import me.wolfii.allthelogs.client.ui.popup.ChecklistMenu;
import me.wolfii.allthelogs.client.ui.popup.OptionMenu;
import me.wolfii.allthelogs.client.ui.text.TextFit;
import me.wolfii.allthelogs.client.ui.text.WrappedTooltip;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.playtime.PlaytimeLegendWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Layout, loading, filter menus, and button labels shared by the timeline and weekday playtime screens.
 * Display options sit in a row above the chart. The legend and the filters share a column on the right.
 */
final class PlaytimeScreenSupport {
    static final int SIDE_WIDTH = 140;
    static final PlaytimeBreakdown[] GROUPS = PlaytimeBreakdown.values();
    /** Space a vanilla button keeps around its label. */
    private static final int BUTTON_PADDING = 12;

    private PlaytimeScreenSupport() {
    }

    static FlowLayout page() {
        FlowLayout page = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        page.gap(4).padding(Insets.of(5));
        page.surface(Surface.blur(5, 10).and(PanelSurfaces.overlay()));
        page.horizontalAlignment(HorizontalAlignment.LEFT);
        page.verticalAlignment(VerticalAlignment.TOP);
        return page;
    }

    /**
     * Row of display options with {@code end} pushed to the right edge.
     */
    static FlowLayout optionsRow(List<UIComponent> options, UIComponent end) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        options.forEach(row::child);
        row.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(0)));
        row.child(end);
        return row;
    }

    /**
     * The chart beside a column with the total, the scrolling legend under it, and the filter controls at the
     * bottom. Only the legend scrolls.
     */
    static FlowLayout body(UIComponent chart, TotalRow total, PlaytimeLegendWidget legend, List<UIComponent> filters) {
        ScrollContainer<PlaytimeLegendWidget> legendScroll = OverflowScrollbar.vertical(Sizing.fill(), Sizing.expand(), legend);
        FlowLayout side = UIContainers.verticalFlow(Sizing.fixed(SIDE_WIDTH), Sizing.fill());
        side.gap(2);
        side.child(total.row);
        side.child(legendScroll);
        filters.forEach(side::child);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
        body.gap(4);
        body.child(chart.horizontalSizing(Sizing.expand()).verticalSizing(Sizing.fill()));
        body.child(side);
        return body;
    }

    /**
     * "Total" on the left and the playtime on the right, in line with the legend's durations below it.
     */
    static final class TotalRow {
        final FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        private final LabelComponent name = UIComponents.label(Component.empty());
        private final LabelComponent value = UIComponents.label(Component.empty());

        TotalRow() {
            row.padding(Insets.right(PlaytimeLegendWidget.VALUE_INSET));
            name.horizontalSizing(Sizing.expand());
            row.child(name);
            row.child(value);
        }

        /**
         * @param duration the formatted total, or null to leave the row empty while loading or on an error
         */
        void show(@Nullable String duration) {
            name.text(duration == null ? Component.empty() : Component.translatable("allthelogs.playtime.total"));
            value.text(duration == null ? Component.empty() : Component.literal(duration));
        }
    }

    static void load(Screen screen, int request, IntSupplier token, Consumer<Component> status, Runnable ready) {
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) {
            status.accept(Component.translatable("allthelogs.meta.unavailable"));
            return;
        }
        if (!PlaytimeSession.loaded()) status.accept(Component.translatable("allthelogs.playtime.loading"));
        worker.playtime().whenComplete((spans, error) -> Minecraft.getInstance().execute(() -> {
            if (request != token.getAsInt() || Minecraft.getInstance().gui.screen() != screen) return;
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("Could not read playtime", error);
                status.accept(Component.translatable("allthelogs.status.error"));
                return;
            }
            PlaytimeSession.spans(spans);
            status.accept(null);
            ready.run();
        }));
    }

    /**
     * Puts the total above the legend, or a loading or error message in the chart.
     */
    static void showTotal(@Nullable TotalRow total, @Nullable Consumer<Component> placeholder,
                          @Nullable Component message, long totalMillis) {
        if (total != null) total.show(message != null ? null : PlaytimeDuration.summary(totalMillis));
        if (placeholder != null) placeholder.accept(message != null ? message : Component.translatable("allthelogs.playtime.empty"));
    }

    static void openVersions(ChecklistMenu menu, ButtonComponent button, Runnable closeMenus, Runnable rebuild) {
        boolean open = menu.isOpen();
        closeMenus.run();
        if (open) return;
        menu.show(button, PlaytimeChartModel.versionsIn(PlaytimeSession.spans()), () -> PlaytimeSession.filter().versions(),
            ActiveFilters::name, selected -> {
                PlaytimeSession.filter(PlaytimeSession.filter().withVersions(selected));
                rebuild.run();
            });
    }

    static void openServers(ChecklistMenu menu, ButtonComponent button, Runnable closeMenus, Runnable rebuild) {
        boolean open = menu.isOpen();
        closeMenus.run();
        if (open) return;
        menu.show(button, PlaytimeChartModel.serversIn(PlaytimeSession.spans()), () -> PlaytimeSession.filter().servers(),
            ActiveFilters::name, selected -> {
                PlaytimeSession.filter(PlaytimeSession.filter().withServers(selected));
                rebuild.run();
            });
    }

    /**
     * Group menu. While the chart is grouped by version, the first row is a checkbox that keeps only the
     * first two version components, so {@code 1.8.9} is drawn as {@code 1.8} and {@code 26.2} stays {@code 26.2}.
     */
    static void openBreakdown(OptionMenu menu, ButtonComponent button, Runnable closeMenus, Runnable rebuild) {
        if (menu.isOpen()) return;
        closeMenus.run();
        menu.show(button, groupChoices(), index -> {
            PlaytimeSession.breakdown(GROUPS[index]);
            rebuild.run();
        }, majorVersionsChoice(rebuild));
    }

    /**
     * The group menu's leading checkbox, or null when the current grouping does not include a version.
     */
    private static @Nullable CheckboxComponent majorVersionsChoice(Runnable rebuild) {
        if (!PlaytimeSession.breakdown().byVersion()) return null;
        CheckboxComponent box = UIComponents.checkbox(Component.translatable("allthelogs.playtime.major_versions"));
        box.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.playtime.major_versions.hint")));
        box.checked(PlaytimeSession.majorVersions());
        box.onChanged(checked -> {
            if (checked == PlaytimeSession.majorVersions()) return;
            PlaytimeSession.majorVersions(checked);
            rebuild.run();
        });
        return box;
    }

    static Component versionLabel() {
        return selection("allthelogs.playtime.versions", PlaytimeSession.filter().versions(), ActiveFilters::name);
    }

    static Component serverLabel() {
        return selection("allthelogs.playtime.servers", PlaytimeSession.filter().servers(), ActiveFilters::name);
    }

    /**
     * Full-width filter button for the side column.
     */
    static ButtonComponent filterButton(Component label, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(label, onPress::accept);
        button.horizontalSizing(Sizing.fill());
        return button;
    }

    /**
     * Button as wide as the longest of {@code labels}, so it keeps its size whichever option is chosen.
     */
    static ButtonComponent optionButton(Component label, List<Component> labels, Consumer<ButtonComponent> onPress) {
        int width = 0;
        for (Component option : labels) width = Math.max(width, Minecraft.getInstance().font.width(option));
        ButtonComponent button = UIComponents.button(label, onPress::accept);
        button.horizontalSizing(Sizing.fixed(width + BUTTON_PADDING));
        return button;
    }

    static ButtonComponent button(String key, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(Component.translatable(key), onPress::accept);
        button.horizontalSizing(Sizing.content());
        return button;
    }

    static Component groupLabel(PlaytimeBreakdown breakdown) {
        return Component.translatable("allthelogs.playtime.breakdown", Component.translatable(groupKey(breakdown)));
    }

    static List<Component> groupLabels() {
        return Arrays.stream(GROUPS).map(PlaytimeScreenSupport::groupLabel).toList();
    }

    static List<Component> groupChoices() {
        return Arrays.stream(GROUPS).map(group -> (Component) Component.translatable(groupKey(group))).toList();
    }

    private static String groupKey(PlaytimeBreakdown breakdown) {
        return switch (breakdown) {
            case VERSION -> "allthelogs.playtime.breakdown.version";
            case VERSION_SERVER -> "allthelogs.playtime.breakdown.version_server";
            case VERSION_PLAYER -> "allthelogs.playtime.breakdown.version_player";
            case SERVER -> "allthelogs.playtime.breakdown.server";
            case SERVER_PLAYER -> "allthelogs.playtime.breakdown.server_player";
            case PLAYER -> "allthelogs.playtime.breakdown.player";
        };
    }

    /**
     * "Versions: All", "Versions: None", the single chosen name, or the count, cut to the side column.
     */
    private static Component selection(String key, Set<String> selected, Function<String, String> name) {
        Component value;
        if (selected == null) value = Component.translatable("allthelogs.playtime.filter.all");
        else if (selected.isEmpty()) value = Component.translatable("allthelogs.playtime.filter.none");
        else if (selected.size() == 1) value = Component.literal(name.apply(selected.iterator().next()));
        else value = Component.literal(Integer.toString(selected.size()));
        return Component.literal(TextFit.fit(Component.translatable(key, value).getString(), SIDE_WIDTH - BUTTON_PADDING));
    }
}
