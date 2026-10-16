package me.wolfii.allthelogs.client.ui.popup;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/**
 * Card in the middle of a screen over a shade that blocks everything behind it. It either asks before an
 * action, with Cancel and a confirm button, or shows that a long action is running. The screen closes it on
 * Escape while it asks, see {@link #isOpen()}.
 */
public final class ModalDialog {
    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private FlowLayout shade;
    private boolean working;

    public ModalDialog(StackLayout overlays, IntSupplier screenWidth) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
    }

    public boolean isOpen() {
        return shade != null && overlays.children().contains(shade);
    }

    /**
     * Whether the dialog shows a running action, which Escape must not close.
     */
    public boolean working() {
        return isOpen() && working;
    }

    public void close() {
        if (isOpen()) overlays.removeChild(shade);
        shade = null;
        working = false;
    }

    /**
     * Asks before {@code onConfirm} runs. The dialog closes before it does.
     */
    public void confirm(Component title, Component body, Component confirmLabel, Runnable onConfirm) {
        FlowLayout card = open(title, body, false);
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).horizontalAlignment(HorizontalAlignment.RIGHT);
        actions.child(UIComponents.button(Component.translatable("gui.cancel"), ignored -> close()));
        ButtonComponent confirm = UIComponents.button(confirmLabel, ignored -> {
            close();
            onConfirm.run();
        });
        actions.child(confirm);
        card.child(actions);
    }

    /**
     * Shows that an action is running until {@link #close()}.
     */
    public void working(Component title, Component body) {
        open(title, body, true);
    }

    private FlowLayout open(Component title, Component body, boolean working) {
        close();
        this.working = working;
        shade = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        shade.surface(Surface.flat(0x88000000));
        shade.padding(Insets.of(24));
        shade.horizontalAlignment(HorizontalAlignment.CENTER);
        shade.verticalAlignment(VerticalAlignment.CENTER);
        shade.positioning(Positioning.absolute(0, 0));
        shade.mouseDown().subscribe((mouse, doubled) -> true);
        shade.mouseScroll().subscribe((x, y, amount) -> true);
        int cardWidth = Math.min(360, Math.max(240, screenWidth.getAsInt() - 48));
        FlowLayout card = UIContainers.verticalFlow(Sizing.fixed(cardWidth), Sizing.content());
        card.gap(8).padding(Insets.of(16)).surface(PanelSurfaces.card());
        card.child(UIComponents.label(title));
        LabelComponent text = UIComponents.label(body);
        text.color(Color.ofArgb(Colors.MUTED));
        text.maxWidth(cardWidth - 32);
        card.child(text);
        shade.child(card);
        overlays.child(shade);
        return card;
    }
}
