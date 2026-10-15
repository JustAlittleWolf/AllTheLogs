package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
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
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Multi-select list. A null selection means every row is checked. The All button selects every value again.
 */
final class ChecklistMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private ParentUIComponent menu;

    ChecklistMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight) {
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

    void show(ButtonComponent anchor, List<String> values, Set<String> selected, Function<String, Component> label,
              BiConsumer<String, Boolean> onToggle, Runnable onAll) {
        close();
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        ButtonComponent all = UIComponents.button(Component.translatable("allthelogs.playtime.filter.all"), ignored -> onAll.run());
        all.horizontalSizing(Sizing.fill());
        items.child(all);
        for (String value : values) {
            boolean checked = selected == null || selected.contains(value);
            CheckboxComponent box = UIComponents.checkbox(label.apply(value));
            box.checked(checked);
            box.horizontalSizing(Sizing.fill());
            box.onChanged(now -> onToggle.accept(value, now));
            items.child(box);
        }
        int width = Math.max(160, anchor.width());
        int maxHeight = Math.max(64, Math.min(220, screenHeight.getAsInt() - anchor.y() - anchor.height() - 12));
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
