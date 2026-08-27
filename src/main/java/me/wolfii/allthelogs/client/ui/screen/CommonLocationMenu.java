package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.files.CommonLogLocations;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Dropdown of {@link CommonLogLocations} shortcuts on {@link ImportScreen}.
 */
final class CommonLocationMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private final Consumer<CommonLogLocations.Location> onSelect;

    private ButtonComponent button;
    private ParentUIComponent menu;
    private CommonLogLocations.Location selected;

    CommonLocationMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                       Consumer<CommonLogLocations.Location> onSelect) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.onSelect = onSelect;
    }

    FlowLayout row() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.import.common")));
        button = UIComponents.button(label(), this::toggle);
        button.horizontalSizing(Sizing.fill());
        row.child(button);
        return row;
    }

    void close() {
        if (open()) {
            overlays.removeChild(menu);
        }
        menu = null;
    }

    private Component label() {
        if (selected == null) {
            return Component.translatable("allthelogs.import.common.custom");
        }
        return Component.literal(selected.displayName());
    }

    private void syncButton() {
        if (button != null) button.setMessage(label());
    }

    private void toggle(ButtonComponent ignored) {
        if (open()) {
            close();
            return;
        }
        openMenu();
    }

    private boolean open() {
        return overlays != null && menu != null && overlays.children().contains(menu);
    }

    private void openMenu() {
        if (overlays == null || button == null) return;
        close();
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        items.child(choice(Component.translatable("allthelogs.import.common.custom"), null));
        List<CommonLogLocations.Location> locations = CommonLogLocations.defaults();
        for (CommonLogLocations.Location location : locations) {
            items.child(choice(Component.literal(location.displayName()), location));
        }
        int width = Math.max(160, button.width());
        int maxHeight = Math.max(48, Math.min(220, screenHeight.getAsInt() - button.y() - button.height() - 12));
        ScrollContainer<FlowLayout> panel = UIContainers.verticalScroll(
            Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        panel.scrollbar(OverflowScrollbar.vanillaFlat());
        panel.surface(PanelSurfaces.menu());
        int menuX = Math.min(button.x(), Math.max(0, screenWidth.getAsInt() - width - 4));
        int menuY = button.y() + button.height();
        if (menuY + maxHeight > screenHeight.getAsInt() - 4) {
            menuY = Math.max(4, button.y() - maxHeight);
        }
        panel.positioning(Positioning.absolute(menuX, menuY));
        menu = panel;
        overlays.child(panel);
    }

    private ButtonComponent choice(Component label, CommonLogLocations.Location location) {
        ButtonComponent choice = UIComponents.button(label, ignored -> {
            close();
            selected = location;
            syncButton();
            onSelect.accept(location);
        });
        choice.horizontalSizing(Sizing.fill());
        return choice;
    }
}
