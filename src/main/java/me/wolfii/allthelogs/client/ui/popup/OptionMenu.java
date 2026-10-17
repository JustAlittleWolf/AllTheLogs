package me.wolfii.allthelogs.client.ui.popup;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

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
    /** Inset so the checkbox box is not flush with the menu edge. */
    private static final int CHECKBOX_PAD = 2;

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
        show(anchor, labels, pick, null);
    }

    /**
     * @param leading checkbox drawn as the first row, or null. Checking it leaves the list open.
     */
    public void show(ButtonComponent anchor, List<Component> labels, IntConsumer pick,
                     @Nullable CheckboxComponent leading) {
        close();
        open(anchor, labels, pick, leading);
    }

    private void open(ButtonComponent anchor, List<Component> labels, IntConsumer pick,
                      @Nullable CheckboxComponent leading) {
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        int rows = labels.size();
        if (leading != null) {
            items.child(checkboxRow(leading));
            rows++;
        }
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
        if (leading != null) width = Math.max(width, checkboxWidth(leading));
        int contentHeight = rows * (ROW_HEIGHT + 1) - 1;
        int room = Math.max(ROW_HEIGHT, Math.max(screenHeight.getAsInt() - anchor.y() - anchor.height(), anchor.y()) - 12);
        int maxHeight = Math.min(contentHeight, Math.min(180, room));
        ScrollContainer<FlowLayout> scroll = OverflowScrollbar.vertical(Sizing.fixed(width), Sizing.fixed(maxHeight), items);
        scroll.surface(PanelSurfaces.menu());
        AnchoredPopup.place(scroll, anchor.x(), anchor.y(), anchor.height(), width, maxHeight,
            screenWidth.getAsInt(), screenHeight.getAsInt());
        menu = scroll;
        overlays.child(scroll);
    }

    /**
     * The checkbox box is shorter than a menu button, so the row is button-height and the box sits in the middle.
     */
    private static FlowLayout checkboxRow(CheckboxComponent box) {
        box.horizontalSizing(Sizing.fill());
        box.verticalSizing(Sizing.fixed(Checkbox.getBoxSize(Minecraft.getInstance().font)));
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(ROW_HEIGHT));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.padding(Insets.horizontal(CHECKBOX_PAD));
        row.child(box);
        return row;
    }

    private static int checkboxWidth(CheckboxComponent box) {
        var font = Minecraft.getInstance().font;
        return font.width(box.getMessage()) + Checkbox.getBoxSize(font) + 4 + CHECKBOX_PAD * 2;
    }
}
