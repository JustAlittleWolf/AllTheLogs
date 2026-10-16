package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The "Done" button in a screen's bottom right corner. It leaves the screen the same way Escape does, so it
 * can be reached with the mouse alone.
 */
public final class DoneButton {
    private DoneButton() {
    }

    public static ButtonComponent of(Screen screen) {
        return UIComponents.button(Component.translatable("gui.done"), ignored -> screen.onClose());
    }
}
