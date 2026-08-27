package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * In-game folder and archive picker. owo-lib has no native file dialog, and Tiny File Dialogs
 * freezes the Minecraft client when GLFW cannot pump events.
 */
public final class FileBrowserScreen extends BaseOwoScreen<FlowLayout> {
    public enum Mode {
        FOLDER, ARCHIVE
    }

    private final Screen parent;
    private final Mode mode;
    private final Consumer<Path> onPicked;
    private Path current;
    private LabelComponent pathLabel;
    private FlowLayout listing;
    private ButtonComponent upButton;
    private ButtonComponent selectButton;

    public FileBrowserScreen(Screen parent, Mode mode, Path initial, Consumer<Path> onPicked) {
        super(title(mode));
        this.parent = parent;
        this.mode = mode;
        this.onPicked = onPicked;
        this.current = FileBrowserListing.startDirectory(initial);
    }

    private static Component title(Mode mode) {
        return Component.translatable(mode == Mode.FOLDER
            ? "allthelogs.import.browse.title.folder"
            : "allthelogs.import.browse.title.archive");
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(8);
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(16))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        root.child(UIComponents.label(title(mode)));

        FlowLayout pathRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        pathRow.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        upButton = UIComponents.button(Component.translatable("allthelogs.import.browse.up"),
            button -> navigate(current.getParent()));
        pathRow.child(upButton);
        pathLabel = UIComponents.label(Component.literal(current.toString()));
        pathLabel.color(Color.ofRgb(0xA0A0A0));
        pathRow.child(pathLabel);
        root.child(pathRow);

        listing = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        listing.gap(2);
        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(
            Sizing.fill(), Sizing.expand(), listing);
        scroll.scrollbar(OverflowScrollbar.vanillaFlat());
        root.child(scroll);

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        if (mode == Mode.FOLDER) {
            selectButton = UIComponents.button(
                Component.translatable("allthelogs.import.browse.select_folder"),
                button -> pick(current));
            actions.child(selectButton);
        }
        actions.child(UIComponents.button(Component.translatable("gui.cancel"), button -> onClose()));
        root.child(actions);

        refresh();
    }

    private void navigate(Path next) {
        if (next == null) return;
        current = next.toAbsolutePath().normalize();
        refresh();
    }

    private void pick(Path path) {
        Minecraft.getInstance().gui.setScreen(parent);
        onPicked.accept(path);
    }

    private void refresh() {
        pathLabel.text(Component.literal(current.toString()));
        upButton.active(current.getParent() != null);
        if (selectButton != null) {
            selectButton.active(Files.isDirectory(current));
        }
        listing.clearChildren();
        try {
            var children = FileBrowserListing.children(current, mode == Mode.ARCHIVE);
            if (children.isEmpty()) {
                listing.child(UIComponents.label(Component.translatable("allthelogs.import.browse.empty"))
                    .color(Color.ofRgb(0xA0A0A0)));
                return;
            }
            for (Path child : children) {
                boolean directory = Files.isDirectory(child);
                String name = child.getFileName().toString();
                if (directory) name += "/";
                listing.child(UIComponents.button(Component.literal(name), button -> {
                    if (directory) {
                        navigate(child);
                    } else {
                        pick(child);
                    }
                }).horizontalSizing(Sizing.fill()));
            }
        } catch (IOException ignored) {
            listing.child(UIComponents.label(Component.translatable("allthelogs.import.browse.inaccessible"))
                .color(Color.ofRgb(0xA0A0A0)));
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
