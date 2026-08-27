package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.data.ChatQuery;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Filter popover and version menu for {@link LogBrowserScreen}.
 */
final class FilterOverlay {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private final Supplier<SearchFilter> filter;
    private final Supplier<List<String>> versions;
    private final Consumer<SearchFilter> onChange;

    private ParentUIComponent filterPanel;
    private ButtonComponent oldestFirst;
    private ButtonComponent newestFirst;
    private ButtonComponent versionButton;
    private ParentUIComponent versionMenu;
    private boolean open;

    FilterOverlay(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                  Supplier<SearchFilter> filter, Supplier<List<String>> versions,
                  Consumer<SearchFilter> onChange) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.filter = filter;
        this.versions = versions;
        this.onChange = onChange;
    }

    boolean isOpen() {
        return open;
    }

    void toggle(ButtonComponent button) {
        if (open) {
            close();
            return;
        }
        open = true;
        filterPanel = buildFilterPanel();
        filterPanel.positioning(Positioning.absolute(
            Math.max(8, screenWidth.getAsInt() - 258),
            button.y() + button.height() + 4));
        overlays.child(filterPanel);
    }

    void close() {
        if (filterPanel != null) {
            overlays.removeChild(filterPanel);
            filterPanel = null;
        }
        oldestFirst = null;
        newestFirst = null;
        versionButton = null;
        closeVersionMenu();
        open = false;
    }

    private ParentUIComponent buildFilterPanel() {
        SearchFilter current = filter.get();
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        content.padding(Insets.of(8));
        content.gap(4);

        content.child(checkbox("allthelogs.filter.regex", current.regex(), value ->
            onChange.accept(filter.get().withRegex(value))));
        content.child(checkbox("allthelogs.filter.case_sensitive", current.caseSensitive(), value ->
            onChange.accept(filter.get().withCaseSensitive(value))));
        content.child(labeledField("allthelogs.filter.context", String.valueOf(current.contextLines()), text -> {
            try {
                onChange.accept(filter.get().withContextLines(Integer.parseInt(text.trim())));
            } catch (RuntimeException ignored) {
            }
        }));

        content.child(UIComponents.label(Component.translatable("allthelogs.filter.sort")));
        FlowLayout sortRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        sortRow.gap(4);
        oldestFirst = sortButton("allthelogs.filter.sort.ascending", ChatQuery.Sort.ASCENDING);
        newestFirst = sortButton("allthelogs.filter.sort.descending", ChatQuery.Sort.DESCENDING);
        sortRow.child(oldestFirst);
        sortRow.child(newestFirst);
        content.child(sortRow);
        syncSortButtons();

        content.child(dateField("allthelogs.filter.from", current.startingAt(), parsed ->
            onChange.accept(filter.get().withStartingAt(parsed))));
        content.child(dateField("allthelogs.filter.until", current.upUntil(), parsed ->
            onChange.accept(filter.get().withUpUntil(parsed))));
        content.child(UIComponents.label(Component.translatable("allthelogs.filter.date_hint"))
            .color(Color.ofRgb(0x888888)));
        content.child(versionRow());

        int panelHeight = Math.max(96, Math.min(screenHeight.getAsInt() - 40, 280));
        ScrollContainer<FlowLayout> panel = UIContainers.verticalScroll(
            Sizing.fixed(240), Sizing.fixed(panelHeight), content);
        panel.scrollbar(OverflowScrollbar.vanillaFlat());
        panel.surface(PanelSurfaces.card());
        return panel;
    }

    private CheckboxComponent checkbox(String key, boolean checked, Consumer<Boolean> onChanged) {
        CheckboxComponent box = UIComponents.checkbox(Component.translatable(key));
        box.checked(checked);
        box.onChanged(onChanged::accept);
        return box;
    }

    private FlowLayout versionRow() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.filter.version")));
        versionButton = UIComponents.button(versionLabel(), this::toggleVersionMenu);
        versionButton.horizontalSizing(Sizing.fill());
        row.child(versionButton);
        return row;
    }

    private Component versionLabel() {
        SearchFilter current = filter.get();
        if (!current.hasVersion()) {
            return Component.translatable("allthelogs.filter.version.all");
        }
        return Component.literal(current.version());
    }

    private void toggleVersionMenu(ButtonComponent button) {
        if (versionMenuOpen()) {
            closeVersionMenu();
            return;
        }
        openVersionMenu(button);
    }

    private boolean versionMenuOpen() {
        return overlays != null && versionMenu != null && overlays.children().contains(versionMenu);
    }

    private void closeVersionMenu() {
        if (versionMenuOpen()) {
            overlays.removeChild(versionMenu);
        }
        versionMenu = null;
    }

    private void openVersionMenu(ButtonComponent button) {
        if (overlays == null) return;
        closeVersionMenu();
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        items.child(versionChoice(Component.translatable("allthelogs.filter.version.all"), null));
        for (String version : MinecraftVersions.newestFirst(versions.get())) {
            items.child(versionChoice(Component.literal(version), version));
        }
        int width = Math.max(120, button.width());
        int maxHeight = Math.max(48, Math.min(180, screenHeight.getAsInt() - button.y() - button.height() - 12));
        ScrollContainer<FlowLayout> menu = UIContainers.verticalScroll(
            Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        menu.scrollbar(OverflowScrollbar.vanillaFlat());
        menu.surface(PanelSurfaces.menu());
        int menuX = Math.min(button.x(), Math.max(0, screenWidth.getAsInt() - width - 4));
        int menuY = button.y() + button.height();
        if (menuY + maxHeight > screenHeight.getAsInt() - 4) {
            menuY = Math.max(4, button.y() - maxHeight);
        }
        menu.positioning(Positioning.absolute(menuX, menuY));
        versionMenu = menu;
        overlays.child(menu);
    }

    private ButtonComponent versionChoice(Component label, String version) {
        ButtonComponent choice = UIComponents.button(label, ignored -> {
            closeVersionMenu();
            onChange.accept(filter.get().withVersion(version));
        });
        choice.horizontalSizing(Sizing.fill());
        return choice;
    }

    private ButtonComponent sortButton(String key, ChatQuery.Sort sort) {
        return UIComponents.button(Component.translatable(key), button -> {
            if (filter.get().sort() == sort) return;
            onChange.accept(filter.get().withSort(sort));
        });
    }

    void syncSortButtons() {
        if (oldestFirst == null || newestFirst == null) return;
        SearchFilter current = filter.get();
        oldestFirst.active(current.sort() != ChatQuery.Sort.ASCENDING);
        newestFirst.active(current.sort() != ChatQuery.Sort.DESCENDING);
        if (versionButton != null) versionButton.setMessage(versionLabel());
    }

    private FlowLayout dateField(String key, LocalDateTime value, Consumer<LocalDateTime> onParsed) {
        return labeledField(key, formatBound(value), text -> {
            if (!DateParser.isBlankOrValid(text)) return;
            onParsed.accept(DateParser.parse(text).orElse(null));
        });
    }

    private FlowLayout labeledField(String key, String value, Consumer<String> onFieldChange) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(32);
        box.onChanged().subscribe(onFieldChange::accept);
        row.child(box);
        return row;
    }

    private static String formatBound(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }
}
