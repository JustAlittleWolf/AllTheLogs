package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.container.ScrollContainer;

/**
 * Vanilla-flat scrollbar that stays hidden when the content already fits.
 */
final class OverflowScrollbar {
    private OverflowScrollbar() {
    }

    static ScrollContainer.Scrollbar vanillaFlat() {
        ScrollContainer.Scrollbar inner = ScrollContainer.Scrollbar.vanillaFlat();
        return (context, x, y, width, height, trackX, trackY, trackWidth, trackHeight,
                lastInteractTime, direction, active) -> {
            if (!active) return;
            inner.draw(context, x, y, width, height, trackX, trackY, trackWidth, trackHeight,
                lastInteractTime, direction, true);
        };
    }
}
