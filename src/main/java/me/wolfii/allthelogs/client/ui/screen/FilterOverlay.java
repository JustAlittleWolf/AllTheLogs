package me.wolfii.allthelogs.client.ui.screen;

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
import me.wolfii.allthelogs.client.ui.text.TextFit;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    /** Shorter than a normal 20px button, and only as wide as the X plus the button border. */
    private static final int RESET_BUTTON_WIDTH = 14;
    private static final int RESET_BUTTON_HEIGHT = 14;
    /** Date boxes are 4px shorter than a normal 20px text box to keep the filter panels compact. */
    static final int DATE_BOX_HEIGHT = 16;
    private static boolean sessionOpen;

    private final FlowLayout host;
    private final Supplier<SearchFilter> filter;
    private final Consumer<SearchFilter> onChange;
    private final Supplier<List<String>> versions;
    private final Consumer<Consumer<List<String>>> serversOrWorlds;
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

    FilterOverlay(FlowLayout host, StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                  Supplier<SearchFilter> filter, Supplier<List<String>> versions,
                  Consumer<Consumer<List<String>>> serversOrWorlds, Consumer<SearchFilter> onChange) {
        this.host = host;
        this.filter = filter;
        this.onChange = onChange;
        this.versions = versions;
        this.serversOrWorlds = serversOrWorlds;
        this.versionMenu = new ChecklistMenu(overlays, screenWidth, screenHeight);
        this.serverMenu = new ChecklistMenu(overlays, screenWidth, screenHeight);
    }

    boolean open() {
        return filterPanel != null && host.children().contains(filterPanel);
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
        sessionOpen = false;
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
        sessionOpen = true;
    }

    private ParentUIComponent buildFilterPanel() {
        SearchFilter current = filter.get();
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        content.padding(Insets.of(6, 6, 3, 3));
        content.gap(4);

        regexBox = checkbox("allthelogs.filter.regex", current.regex(), value ->
            emit(filter.get().withRegex(value)));
        content.child(regexBox);
        caseBox = checkbox("allthelogs.filter.case_sensitive", current.caseSensitive(), value ->
            emit(filter.get().withCaseSensitive(value)));
        content.child(caseBox);
        content.child(contextField(current));

        content.child(dateField("allthelogs.filter.from", DateParser.format(current.startingAt()),
            DateParser::parse, parsed -> emit(filter.get().withStartingAt(parsed)), true));
        content.child(dateField("allthelogs.filter.until", DateParser.formatUntil(current.upUntil()),
            DateParser::parseUntil, parsed -> emit(filter.get().withUpUntil(parsed)), false));
        versionButton = listButton(current.versions(), Function.identity(), this::openVersions);
        content.child(listRow("allthelogs.filter.version", versionButton,
            () -> emit(filter.get().withVersions(null))));
        serverButton = listButton(current.serversOrWorlds(), FilterOverlay::placeName, this::openServers);
        content.child(listRow("allthelogs.filter.server", serverButton,
            () -> emit(filter.get().withServersOrWorlds(null))));

        ScrollContainer<FlowLayout> panel = UIContainers.verticalScroll(
            Sizing.fixed(PANEL_WIDTH), Sizing.fill(), content);
        panel.scrollbar(OverflowScrollbar.vanillaFlat());
        panel.surface(PanelSurfaces.card());
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
        row.child(resetHeader(key, reset));
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
        List<String> values = MinecraftVersions.newestFirst(versions.get());
        versionMenu.show(button, values, () -> filter.get().versions(), Function.identity(),
            selected -> emit(filter.get().withVersions(selected)));
    }

    private void openServers(ButtonComponent button) {
        boolean open = serverMenu.isOpen();
        closeLists();
        if (open) return;
        serversOrWorlds.accept(stored -> {
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

    private FlowLayout dateField(String key, String value, Function<String, Optional<LocalDateTime>> parse,
                                 Consumer<LocalDateTime> onParsed, boolean from) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(resetHeader(key, () -> onParsed.accept(null)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.verticalSizing(Sizing.fixed(DATE_BOX_HEIGHT));
        box.setMaxLength(32);
        box.setHint(Component.translatable("allthelogs.filter.date_hint"));
        box.onChanged().subscribe(text -> {
            if (syncing) return;
            if (!DateParser.isBlankOrValid(text)) return;
            onParsed.accept(parse.apply(text).orElse(null));
        });
        row.child(box);
        if (from) fromBox = box;
        else untilBox = box;
        return row;
    }

    /**
     * Label with a narrow X on the right. The X is a Minecraft font glyph, and the button clears that one field.
     */
    static FlowLayout resetHeader(String key, Runnable reset) {
        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        header.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        header.child(UIComponents.label(Component.translatable(key)).horizontalSizing(Sizing.expand()));
        ButtonComponent clear = UIComponents.button(Component.literal("❌"), ignored -> reset.run());
        clear.horizontalSizing(Sizing.fixed(RESET_BUTTON_WIDTH));
        clear.verticalSizing(Sizing.fixed(RESET_BUTTON_HEIGHT));
        header.child(clear);
        return header;
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
