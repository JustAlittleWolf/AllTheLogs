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
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Small choice list anchored under a button. Choosing an entry closes the list.
 */
final class OptionMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private ParentUIComponent menu;

    OptionMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    boolean isOpen() {
        return menu != null && overlays.children().contains(menu);
    }

    void close() {
        if (isOpen()) overlays.removeChild(menu);
        menu = null;
    }

    void show(ButtonComponent anchor, List<Component> labels, IntConsumer pick) {
        close();
        open(anchor, labels, pick);
    }

    private void open(ButtonComponent anchor, List<Component> labels, IntConsumer pick) {
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        for (int index = 0; index < labels.size(); index++) {
            int chosen = index;
            ButtonComponent button = UIComponents.button(labels.get(index), ignored -> {
                close();
                pick.accept(chosen);
            });
            button.horizontalSizing(Sizing.fill());
            items.child(button);
        }
        int width = Math.max(120, anchor.width());
        int maxHeight = Math.max(48, Math.min(180, screenHeight.getAsInt() - anchor.y() - anchor.height() - 12));
        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        scroll.scrollbar(OverflowScrollbar.vanillaFlat());
        scroll.surface(PanelSurfaces.menu());
        int menuX = Math.min(anchor.x(), Math.max(0, screenWidth.getAsInt() - width - 4));
        int menuY = anchor.y() + anchor.height();
        if (menuY + maxHeight > screenHeight.getAsInt() - 4) menuY = Math.max(4, anchor.y() - maxHeight);
        scroll.positioning(Positioning.absolute(menuX, menuY));
        menu = scroll;
        overlays.child(scroll);
    }
}
