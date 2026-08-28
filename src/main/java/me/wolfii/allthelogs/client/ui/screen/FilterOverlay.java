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
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
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
    private final Consumer<SearchFilter> onChange;
    private final VersionMenu versionsMenu;
    private ParentUIComponent filterPanel;
    private boolean open;

    FilterOverlay(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                  Supplier<SearchFilter> filter, Supplier<List<String>> versions,
                  Consumer<SearchFilter> onChange) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.filter = filter;
        this.onChange = onChange;
        this.versionsMenu = new VersionMenu(overlays, screenWidth, screenHeight, filter, versions, onChange);
    }

    private static String formatBound(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    void toggle(ButtonComponent button) {
        if (open) {
            close();
            return;
        }
        open = true;
        int panelHeight = Math.max(96, Math.min(screenHeight.getAsInt() - 40, 280));
        int below = button.y() + button.height() + 4;
        int above = button.y() - panelHeight - 4;
        int panelY = below + panelHeight > screenHeight.getAsInt() - 8 ? Math.max(8, above) : below;
        filterPanel = buildFilterPanel();
        filterPanel.positioning(Positioning.absolute(
            Math.max(8, screenWidth.getAsInt() - 258),
            panelY));
        overlays.child(filterPanel);
    }

    void close() {
        if (filterPanel != null) {
            overlays.removeChild(filterPanel);
            filterPanel = null;
        }
        versionsMenu.close();
        open = false;
    }

    /**
     * Whether {@code component} is inside the open filter panel.
     */
    boolean owns(UIComponent component) {
        if (!open || component == null) return false;
        for (UIComponent current = component; current != null; current = current.parent()) {
            if (current == filterPanel) return true;
        }
        return false;
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

        content.child(dateField("allthelogs.filter.from", current.startingAt(), parsed ->
            onChange.accept(filter.get().withStartingAt(parsed))));
        content.child(dateField("allthelogs.filter.until", current.upUntil(), parsed ->
            onChange.accept(filter.get().withUpUntil(parsed))));
        content.child(UIComponents.label(Component.translatable("allthelogs.filter.date_hint"))
            .color(Color.ofRgb(0x888888)));
        content.child(versionsMenu.row());

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

    void syncVersionButton() {
        versionsMenu.syncButton();
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
}
