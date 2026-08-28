package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.files.ImportTimezones;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Timezone text field with filtered suggestions of {@link ImportTimezones#choices()}.
 */
final class TimezonePicker {
    private static final int MAX_SUGGESTIONS = 10;

    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private final Consumer<ImportTimezones.Choice> onSelect;

    private TextBoxComponent box;
    private ParentUIComponent menu;
    private boolean updating;

    TimezonePicker(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                   Consumer<ImportTimezones.Choice> onSelect) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.onSelect = onSelect;
    }

    /**
     * Label, autocomplete field, and an optional control on the same row (summer time).
     */
    FlowLayout row(ZoneId initial, UIComponent beside) {
        FlowLayout column = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        column.gap(2);
        column.child(UIComponents.label(Component.translatable("allthelogs.import.timezone")));
        ImportTimezones.Choice current = choiceFor(initial);
        box = UIComponents.textBox(Sizing.expand(), current.label());
        box.setMaxLength(128);
        box.onChanged().subscribe(this::onTyped);
        FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        line.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        line.child(box);
        if (beside != null) {
            line.child(beside);
        }
        column.child(line);
        return column;
    }

    String text() {
        return box == null ? "" : box.getValue();
    }

    void setText(String value) {
        if (box == null) return;
        updating = true;
        box.text(value);
        updating = false;
    }

    void close() {
        if (open()) {
            overlays.removeChild(menu);
        }
        menu = null;
    }

    private void onTyped(String value) {
        if (updating) return;
        ImportTimezones.parse(value).ifPresent(zone ->
            ImportTimezones.choices().stream()
                .filter(choice -> choice.zone().equals(zone) || choice.zone().getId().equals(zone.getId()))
                .findFirst()
                .ifPresentOrElse(onSelect, () -> onSelect.accept(ImportTimezones.Choice.of(zone, java.time.Instant.now()))));
        showSuggestions(value);
    }

    private void showSuggestions(String query) {
        if (overlays == null || box == null) return;
        close();
        List<ImportTimezones.Choice> matches = ImportTimezones.matching(query);
        if (matches.isEmpty()) return;
        if (matches.size() > MAX_SUGGESTIONS) {
            matches = matches.subList(0, MAX_SUGGESTIONS);
        }
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        for (ImportTimezones.Choice choice : matches) {
            items.child(choiceButton(choice));
        }
        int width = Math.max(220, box.width());
        int maxHeight = Math.max(48, Math.min(180, screenHeight.getAsInt() - box.y() - box.height() - 12));
        ScrollContainer<FlowLayout> panel = UIContainers.verticalScroll(
            Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        panel.scrollbar(OverflowScrollbar.vanillaFlat());
        panel.surface(PanelSurfaces.menu());
        int menuX = Math.min(box.x(), Math.max(0, screenWidth.getAsInt() - width - 4));
        int menuY = box.y() + box.height();
        if (menuY + maxHeight > screenHeight.getAsInt() - 4) {
            menuY = Math.max(4, box.y() - maxHeight);
        }
        panel.positioning(Positioning.absolute(menuX, menuY));
        menu = panel;
        overlays.child(panel);
    }

    private ButtonComponent choiceButton(ImportTimezones.Choice choice) {
        ButtonComponent button = UIComponents.button(Component.literal(choice.label()), ignored -> {
            close();
            setText(choice.label());
            onSelect.accept(choice);
        });
        button.horizontalSizing(Sizing.fill());
        return button;
    }

    private static ImportTimezones.Choice choiceFor(ZoneId zone) {
        for (ImportTimezones.Choice choice : ImportTimezones.choices()) {
            if (choice.zone().equals(zone) || choice.zone().getId().equals(zone.getId())) {
                return choice;
            }
        }
        return ImportTimezones.Choice.of(zone, java.time.Instant.now());
    }

    private boolean open() {
        return overlays != null && menu != null && overlays.children().contains(menu);
    }
}
