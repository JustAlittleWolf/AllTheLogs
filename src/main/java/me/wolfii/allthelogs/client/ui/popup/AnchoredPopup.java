package me.wolfii.allthelogs.client.ui.popup;

import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.UIComponent;

/**
 * Places a popup under an anchor, or above it when the popup would leave the screen.
 */
public final class AnchoredPopup {
    private AnchoredPopup() {
    }

    public static void place(UIComponent popup, int anchorX, int anchorY, int anchorHeight,
                      int popupWidth, int popupHeight, int screenWidth, int screenHeight) {
        int menuX = Math.min(anchorX, Math.max(0, screenWidth - popupWidth - 4));
        int menuY = anchorY + anchorHeight;
        if (menuY + popupHeight > screenHeight - 4) menuY = Math.max(4, anchorY - popupHeight);
        popup.positioning(Positioning.absolute(menuX, menuY));
    }
}
