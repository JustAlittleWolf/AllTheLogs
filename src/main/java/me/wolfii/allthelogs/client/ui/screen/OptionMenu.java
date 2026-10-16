package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Small choice list anchored under a button and exactly as wide. Choosing an entry closes the list. With
 * {@link #opensOnHover} it behaves like the messages menu: hovering the button opens it, and it closes
 * {@link #CLOSE_DELAY_MS} after the pointer leaves both the button and the list, like the messages menu.
 */
final class OptionMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private static final int ROW_HEIGHT = 20;
    static final long CLOSE_DELAY_MS = 1000;

    private ParentUIComponent menu;
    private ButtonComponent hoverAnchor;
    private Runnable hoverOpen;
    private long closeAt = Long.MAX_VALUE;

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
        closeAt = Long.MAX_VALUE;
    }

    /**
     * Opens through {@code open} whenever the pointer is on {@code anchor}. Call {@link #sync} every frame.
     */
    void opensOnHover(ButtonComponent anchor, Runnable open) {
        this.hoverAnchor = anchor;
        this.hoverOpen = open;
    }

    void sync(int mouseX, int mouseY) {
        if (hoverAnchor == null) return;
        boolean over = hit(hoverAnchor, mouseX, mouseY) || (isOpen() && hit(menu, mouseX, mouseY));
        long now = System.currentTimeMillis();
        if (over) {
            closeAt = Long.MAX_VALUE;
            if (!isOpen()) hoverOpen.run();
            return;
        }
        if (!isOpen()) return;
        if (closeAt == Long.MAX_VALUE) closeAt = now + CLOSE_DELAY_MS;
        if (now >= closeAt) close();
    }

    private static boolean hit(UIComponent component, int mouseX, int mouseY) {
        if (component == null || component.width() <= 0 || component.height() <= 0) return false;
        return mouseX >= component.x() && mouseX < component.x() + component.width()
            && mouseY >= component.y() && mouseY < component.y() + component.height();
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
        int width = anchor.width();
        int contentHeight = labels.size() * (ROW_HEIGHT + 1) - 1;
        int room = Math.max(ROW_HEIGHT, Math.max(screenHeight.getAsInt() - anchor.y() - anchor.height(), anchor.y()) - 12);
        int maxHeight = Math.min(contentHeight, Math.min(180, room));
        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        scroll.scrollbar(OverflowScrollbar.vanillaFlat());
        scroll.surface(PanelSurfaces.menu());
        AnchoredPopup.place(scroll, anchor.x(), anchor.y(), anchor.height(), width, maxHeight,
            screenWidth.getAsInt(), screenHeight.getAsInt());
        menu = scroll;
        overlays.child(scroll);
    }
}
