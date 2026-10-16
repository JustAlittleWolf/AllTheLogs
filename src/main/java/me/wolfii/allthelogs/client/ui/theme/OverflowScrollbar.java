package me.wolfii.allthelogs.client.ui.theme;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Vanilla-flat scrollbar that stays hidden when the content already fits.
 */
public final class OverflowScrollbar {
    private OverflowScrollbar() {
    }

    /**
     * Vertical scroll list with this scrollbar. owo draws the bar over the right edge of the content and
     * still hands a click on it to the row underneath, which toggled checkboxes when the bar was grabbed.
     * Here a click on the bar only scrolls. While everything fits, the bar is hidden and rows keep their clicks.
     */
    public static <C extends UIComponent> ScrollContainer<C> vertical(Sizing horizontal, Sizing vertical, C child) {
        ScrollContainer<C> scroll = new ScrollContainer<>(ScrollContainer.ScrollDirection.VERTICAL, horizontal,
            vertical, child) {
            @Override
            public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
                if (maxScroll > 0 && isInScrollbar(x + click.x(), y + click.y())) return true;
                return super.onMouseDown(click, doubled);
            }
        };
        scroll.scrollbar(vanillaFlat());
        return scroll;
    }

    public static ScrollContainer.Scrollbar vanillaFlat() {
        ScrollContainer.Scrollbar inner = ScrollContainer.Scrollbar.vanillaFlat();
        return (context, x, y, width, height, trackX, trackY, trackWidth, trackHeight,
                lastInteractTime, direction, active) -> {
            if (!active) return;
            inner.draw(context, x, y, width, height, trackX, trackY, trackWidth, trackHeight,
                lastInteractTime, direction, true);
        };
    }
}
