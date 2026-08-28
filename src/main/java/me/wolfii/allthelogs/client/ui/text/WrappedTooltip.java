package me.wolfii.allthelogs.client.ui.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Import-form tooltips wrapped to a shared max width so long glob and option help does not run off-screen.
 */
public final class WrappedTooltip {
    public static final int MAX_WIDTH = 200;

    private WrappedTooltip() {
    }

    public static List<ClientTooltipComponent> of(Component text) {
        Minecraft client = Minecraft.getInstance();
        Font font = client == null ? null : client.font;
        if (font == null) {
            return List.of(ClientTooltipComponent.create(text.getVisualOrderText()));
        }
        return of(text, font, MAX_WIDTH);
    }

    static List<ClientTooltipComponent> of(Component text, Font font, int maxWidth) {
        List<ClientTooltipComponent> lines = new ArrayList<>();
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            lines.add(ClientTooltipComponent.create(line));
        }
        return List.copyOf(lines);
    }
}
