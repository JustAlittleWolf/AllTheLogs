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
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Server or world dropdown for {@link FilterOverlay}.
 */
final class ServerMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private final Supplier<SearchFilter> filter;
    private final Supplier<List<String>> servers;
    private final Consumer<SearchFilter> onChange;

    private ButtonComponent serverButton;
    private ParentUIComponent serverMenu;

    ServerMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                Supplier<SearchFilter> filter, Supplier<List<String>> servers,
                Consumer<SearchFilter> onChange) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.filter = filter;
        this.servers = servers;
        this.onChange = onChange;
    }

    FlowLayout row() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.filter.server")));
        serverButton = UIComponents.button(label(), this::toggle);
        serverButton.horizontalSizing(Sizing.fill());
        row.child(serverButton);
        return row;
    }

    void syncButton() {
        if (serverButton != null) serverButton.setMessage(label());
    }

    void close() {
        if (open()) {
            overlays.removeChild(serverMenu);
        }
        serverMenu = null;
    }

    private Component label() {
        SearchFilter current = filter.get();
        if (!current.hasServerOrWorld()) {
            return Component.translatable("allthelogs.filter.server.all");
        }
        return Component.literal(current.serverOrWorld());
    }

    private void toggle(ButtonComponent button) {
        if (open()) {
            close();
            return;
        }
        open(button);
    }

    private boolean open() {
        return overlays != null && serverMenu != null && overlays.children().contains(serverMenu);
    }

    private void open(ButtonComponent button) {
        if (overlays == null) return;
        close();
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        items.child(choice(Component.translatable("allthelogs.filter.server.all"), null));
        for (String place : servers.get()) {
            items.child(choice(Component.literal(place), place));
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
        serverMenu = menu;
        overlays.child(menu);
    }

    private ButtonComponent choice(Component label, String place) {
        ButtonComponent choice = UIComponents.button(label, ignored -> {
            close();
            onChange.accept(filter.get().withServerOrWorld(place));
        });
        choice.horizontalSizing(Sizing.fill());
        return choice;
    }
}
