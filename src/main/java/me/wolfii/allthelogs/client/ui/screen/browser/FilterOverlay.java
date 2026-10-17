package me.wolfii.allthelogs.client.ui.screen.browser;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.popup.ChecklistMenu;
import me.wolfii.allthelogs.client.ui.text.TextFit;
import me.wolfii.allthelogs.client.ui.text.WrappedTooltip;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.DateField;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Side-expandable filter panel for {@link LogBrowserScreen}. It stays open while the user searches.
 */
final class FilterOverlay {
    static final int PANEL_WIDTH = 144;
    private static boolean sessionOpen;

    private final FlowLayout host;
    private final Supplier<SearchFilter> filter;
    private final Consumer<SearchFilter> onChange;
    private final StoreCatalog catalog;
    private final ChecklistMenu versionMenu;
    private final ChecklistMenu serverMenu;
    private ButtonComponent versionButton;
    private ButtonComponent serverButton;
    private ParentUIComponent filterPanel;
    private CheckboxComponent regexBox;
    private CheckboxComponent caseBox;
    private TextBoxComponent contextBox;
    private TextBoxComponent fromBox;
    private TextBoxComponent untilBox;
    private boolean syncing;
    private boolean detached;

    FilterOverlay(FlowLayout host, StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                  Supplier<SearchFilter> filter, StoreCatalog catalog, Consumer<SearchFilter> onChange) {
        this.host = host;
        this.filter = filter;
        this.onChange = onChange;
        this.catalog = catalog;
        this.versionMenu = new ChecklistMenu(overlays, screenWidth, screenHeight);
        this.serverMenu = new ChecklistMenu(overlays, screenWidth, screenHeight);
    }

    boolean open() {
        return filterPanel != null && host.children().contains(filterPanel);
    }

    /**
     * Opens the panel on a screen other than the messages screen: without the context lines field, and
     * without changing whether the messages screen reopens its panel.
     */
    void openDetached() {
        detached = true;
        openPanel();
    }

    void restore() {
        if (sessionOpen) openPanel();
    }

    void toggle() {
        if (open()) {
            close();
            return;
        }
        openPanel();
    }

    /**
     * Closes the version or server list when a click lands outside it and its button.
     */
    void closeListsOnClickOutside(double mouseX, double mouseY) {
        versionMenu.closeOnClickOutside(mouseX, mouseY);
        serverMenu.closeOnClickOutside(mouseX, mouseY);
    }

    boolean listOpen() {
        return versionMenu.isOpen() || serverMenu.isOpen();
    }

    void closeLists() {
        versionMenu.close();
        serverMenu.close();
    }

    void close() {
        closeLists();
        if (filterPanel != null) {
            host.removeChild(filterPanel);
            filterPanel = null;
        }
        if (!detached) sessionOpen = false;
    }

    /**
     * Whether {@code component} is inside the open filter panel.
     */
    boolean owns(UIComponent component) {
        if (!open() || component == null) return false;
        for (UIComponent current = component; current != null; current = current.parent()) {
            if (current == filterPanel) return true;
        }
        return false;
    }

    void syncFromFilter() {
        SearchFilter current = filter.get();
        syncing = true;
        try {
            if (regexBox != null) regexBox.checked(current.regex());
            if (caseBox != null) caseBox.checked(current.caseSensitive());
            if (contextBox != null && !contextBox.getValue().equals(String.valueOf(current.contextLines()))) {
                contextBox.setValue(String.valueOf(current.contextLines()));
            }
            String from = DateParser.format(current.startingAt());
            if (fromBox != null && !fromBox.getValue().equals(from)) fromBox.setValue(from);
            String until = DateParser.formatUntil(current.upUntil());
            if (untilBox != null && !untilBox.getValue().equals(until)) untilBox.setValue(until);
            syncFilterButtons();
        } finally {
            syncing = false;
        }
    }

    void syncFilterButtons() {
        SearchFilter current = filter.get();
        if (versionButton != null) versionButton.setMessage(selection(current.versions(), Function.identity()));
        if (serverButton != null) serverButton.setMessage(selection(current.serversOrWorlds(), FilterOverlay::placeName));
    }

    private void openPanel() {
        if (open()) return;
        filterPanel = buildFilterPanel();
        host.child(filterPanel);
        if (!detached) sessionOpen = true;
    }

    private ParentUIComponent buildFilterPanel() {
        SearchFilter current = filter.get();
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        content.padding(Insets.of(6, 6, 3, 3));
        content.gap(4);

        regexBox = checkbox("allthelogs.filter.regex", current.regex(), value ->
            emit(filter.get().withRegex(value)));
        regexBox.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.filter.regex.hint")));
        content.child(regexBox);
        caseBox = checkbox("allthelogs.filter.case_sensitive", current.caseSensitive(), value ->
            emit(filter.get().withCaseSensitive(value)));
        content.child(caseBox);
        if (!detached) content.child(contextField(current));

        DateField from = new DateField("allthelogs.filter.from", DateParser.format(current.startingAt()),
            DateParser::parse, parsed -> {
                if (!syncing) emit(filter.get().withStartingAt(parsed));
            });
        DateField until = new DateField("allthelogs.filter.until", DateParser.formatUntil(current.upUntil()),
            DateParser::parseUntil, parsed -> {
                if (!syncing) emit(filter.get().withUpUntil(parsed));
            });
        fromBox = from.box();
        untilBox = until.box();
        content.child(from.row());
        content.child(until.row());
        versionButton = listButton(current.versions(), Function.identity(), this::openVersions);
        content.child(listRow("allthelogs.filter.version", versionButton,
            () -> emit(filter.get().withVersions(null))));
        serverButton = listButton(current.serversOrWorlds(), FilterOverlay::placeName, this::openServers);
        content.child(listRow("allthelogs.filter.server", serverButton,
            () -> emit(filter.get().withServersOrWorlds(null))));

        ScrollContainer<FlowLayout> panel = OverflowScrollbar.vertical(
            Sizing.fixed(PANEL_WIDTH), Sizing.fill(), content);
        panel.surface(detached ? PanelSurfaces.card() : PanelSurfaces.filterSidebar());
        panel.margins(Insets.none());
        panel.padding(Insets.none());
        panel.verticalSizing(Sizing.fill());
        return panel;
    }

    /**
     * Label with a clear button over a button that opens a searchable multi-select list.
     */
    private FlowLayout listRow(String key, ButtonComponent button, Runnable reset) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(DateField.resetHeader(key, reset));
        row.child(button);
        return row;
    }

    private static ButtonComponent listButton(Set<String> selected, Function<String, String> name,
                                              Consumer<ButtonComponent> open) {
        ButtonComponent button = UIComponents.button(selection(selected, name), open::accept);
        button.horizontalSizing(Sizing.fill());
        return button;
    }

    private void openVersions(ButtonComponent button) {
        boolean open = versionMenu.isOpen();
        closeLists();
        if (open) return;
        List<String> values = MinecraftVersions.newestFirst(catalog.versions());
        versionMenu.show(button, values, () -> filter.get().versions(), Function.identity(),
            selected -> emit(filter.get().withVersions(selected)));
    }

    private void openServers(ButtonComponent button) {
        boolean open = serverMenu.isOpen();
        closeLists();
        if (open) return;
        catalog.serversOrWorlds(stored -> {
            if (!open() || serverMenu.isOpen()) return;
            List<String> values = new ArrayList<>(stored);
            values.sort(Comparator.comparing(FilterOverlay::placeName, String.CASE_INSENSITIVE_ORDER));
            values.add(ChatQuery.NO_SERVER_OR_WORLD);
            serverMenu.show(button, values, () -> filter.get().serversOrWorlds(), FilterOverlay::placeName,
                selected -> emit(filter.get().withServersOrWorlds(selected)));
        });
    }

    /**
     * "All", "None", the one chosen name, or how many are chosen.
     */
    private static Component selection(Set<String> selected, Function<String, String> name) {
        if (selected == null) return Component.translatable("allthelogs.playtime.filter.all");
        if (selected.isEmpty()) return Component.translatable("allthelogs.playtime.filter.none");
        if (selected.size() == 1) {
            return Component.literal(TextFit.fit(name.apply(selected.iterator().next()), PANEL_WIDTH - 20));
        }
        return Component.translatable("allthelogs.filter.selected", selected.size());
    }

    /**
     * Display name of a server or world, without formatting codes; "Unknown" for messages without one.
     */
    static String placeName(String place) {
        if (place == null || place.isEmpty()) {
            return Component.translatable("allthelogs.playtime.server.unknown").getString();
        }
        return PlaytimeSeries.displayName(place);
    }

    private FlowLayout contextField(SearchFilter current) {
        return labeledField("allthelogs.filter.context", String.valueOf(current.contextLines()), text -> {
            try {
                emit(filter.get().withContextLines(Integer.parseInt(text.trim())));
            } catch (RuntimeException ignored) {
            }
        }, true);
    }

    private CheckboxComponent checkbox(String key, boolean checked, Consumer<Boolean> onChanged) {
        CheckboxComponent box = UIComponents.checkbox(Component.translatable(key));
        box.checked(checked);
        box.onChanged(value -> {
            if (!syncing) onChanged.accept(value);
        });
        return box;
    }

    private FlowLayout labeledField(String key, String value, Consumer<String> onFieldChange, boolean context) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(32);
        box.onChanged().subscribe(text -> {
            if (!syncing) onFieldChange.accept(text);
        });
        row.child(box);
        if (context) contextBox = box;
        return row;
    }

    private void emit(SearchFilter next) {
        if (syncing) return;
        onChange.accept(next);
        syncFromFilter();
    }
}
