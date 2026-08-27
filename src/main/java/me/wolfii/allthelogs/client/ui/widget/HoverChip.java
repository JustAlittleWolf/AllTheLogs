package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.OwoUIGraphics;

/**
 * Filled rounded-looking chip used for hover cards and status banners.
 */
final class HoverChip {
    static final int BORDER = 0xFF3C3C3C;

    private HoverChip() {
    }

    static void fill(OwoUIGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
    }
}
