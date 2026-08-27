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
        return Surface.flat(0xE0101010).and(Surface.outline(0xFF3C3C3C));
    }

    public static Surface menu() {
        return Surface.flat(0xF0101010).and(Surface.outline(0xFF3C3C3C));
    }
}
