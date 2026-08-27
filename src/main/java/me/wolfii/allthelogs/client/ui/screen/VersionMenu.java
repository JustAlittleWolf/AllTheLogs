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
import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Version dropdown for {@link FilterOverlay}.
 */
final class VersionMenu {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private final Supplier<SearchFilter> filter;
    private final Supplier<List<String>> versions;
    private final Consumer<SearchFilter> onChange;

    private ButtonComponent versionButton;
    private ParentUIComponent versionMenu;

    VersionMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                Supplier<SearchFilter> filter, Supplier<List<String>> versions,
                Consumer<SearchFilter> onChange) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.filter = filter;
        this.versions = versions;
        this.onChange = onChange;
    }

    FlowLayout row() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.filter.version")));
        versionButton = UIComponents.button(label(), this::toggle);
        versionButton.horizontalSizing(Sizing.fill());
        row.child(versionButton);
        return row;
    }

    void syncButton() {
        if (versionButton != null) versionButton.setMessage(label());
    }

    void close() {
        if (open()) {
            overlays.removeChild(versionMenu);
        }
        versionMenu = null;
    }

    private Component label() {
        SearchFilter current = filter.get();
        if (!current.hasVersion()) {
            return Component.translatable("allthelogs.filter.version.all");
        }
        return Component.literal(current.version());
    }

    private void toggle(ButtonComponent button) {
        if (open()) {
            close();
            return;
        }
        open(button);
    }

    private boolean open() {
        return overlays != null && versionMenu != null && overlays.children().contains(versionMenu);
    }

    private void open(ButtonComponent button) {
        if (overlays == null) return;
        close();
        FlowLayout items = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        items.gap(1);
        items.child(choice(Component.translatable("allthelogs.filter.version.all"), null));
        for (String version : MinecraftVersions.newestFirst(versions.get())) {
            items.child(choice(Component.literal(version), version));
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

    private ButtonComponent choice(Component label, String version) {
        ButtonComponent choice = UIComponents.button(label, ignored -> {
            close();
            onChange.accept(filter.get().withVersion(version));
        });
        choice.horizontalSizing(Sizing.fill());
        return choice;
    }
}
