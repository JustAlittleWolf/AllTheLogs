package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.core.Surface;

/**
 * Shared panel chrome for the log browser and import screens.
 */
final class BrowserPanels {
    private BrowserPanels() {
    }

    static Surface overlay() {
        return Surface.flat(0x4D000000);
    }

    static Surface card() {
        return Surface.flat(0xE0101010).and(Surface.outline(0xFF3C3C3C));
    }

    static Surface menu() {
        return Surface.flat(0xF0101010).and(Surface.outline(0xFF3C3C3C));
    }
}
