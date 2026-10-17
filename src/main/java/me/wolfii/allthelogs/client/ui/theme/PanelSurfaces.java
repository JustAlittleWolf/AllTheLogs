package me.wolfii.allthelogs.client.ui.theme;

import io.wispforest.owo.ui.core.Surface;

/**
 * Shared panel chrome for the log browser and import screens.
 */
public final class PanelSurfaces {
    private PanelSurfaces() {
    }

    public static Surface overlay() {
        return Surface.flat(0x4D000000);
    }

    public static Surface card() {
        return Surface.flat(0xFF101010).and(Surface.outline(0xFF3C3C3C));
    }

    /**
     * Filter sidebar on the messages screen: the card fill and the edge against the message list.
     */
    public static Surface filterSidebar() {
        return Surface.flat(0xFF101010).and((graphics, component) -> graphics.fill(
            component.x(), component.y(), component.x() + 1, component.y() + component.height(), 0xFF3C3C3C));
    }

    public static Surface menu() {
        return Surface.flat(0xFF101010).and(Surface.outline(0xFF3C3C3C));
    }
}
