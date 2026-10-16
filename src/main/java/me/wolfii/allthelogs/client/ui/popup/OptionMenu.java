package me.wolfii.allthelogs.client.ui.popup;

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
 * {@link #opensOnHover}, hovering the button opens it, and it closes as soon as the pointer leaves both the
 * button and the list, which sits directly under the button.
 */
public final class OptionMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private static final int ROW_HEIGHT = 20;

    private ParentUIComponent menu;
    private ButtonComponent hoverAnchor;
    private Runnable hoverOpen;

    public OptionMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public boolean isOpen() {
        return menu != null && overlays.children().contains(menu);
    }

    public void close() {
        if (isOpen()) overlays.removeChild(menu);
        menu = null;
    }

    /**
     * Opens through {@code open} whenever the pointer is on {@code anchor}. Call {@link #sync} every frame.
     */
    public void opensOnHover(ButtonComponent anchor, Runnable open) {
        this.hoverAnchor = anchor;
        this.hoverOpen = open;
    }

    public void sync(int mouseX, int mouseY) {
        if (hoverAnchor == null) return;
        boolean over = hit(hoverAnchor, mouseX, mouseY) || (isOpen() && hit(menu, mouseX, mouseY));
        if (over && !isOpen()) hoverOpen.run();
        else if (!over && isOpen()) close();
    }

    private static boolean hit(UIComponent component, int mouseX, int mouseY) {
        if (component == null || component.width() <= 0 || component.height() <= 0) return false;
        return mouseX >= component.x() && mouseX < component.x() + component.width()
            && mouseY >= component.y() && mouseY < component.y() + component.height();
    }

    public void show(ButtonComponent anchor, List<Component> labels, IntConsumer pick) {
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
        ScrollContainer<FlowLayout> scroll = OverflowScrollbar.vertical(Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        scroll.surface(PanelSurfaces.menu());
        AnchoredPopup.place(scroll, anchor.x(), anchor.y(), anchor.height(), width, maxHeight,
            screenWidth.getAsInt(), screenHeight.getAsInt());
        menu = scroll;
        overlays.child(scroll);
    }
}
