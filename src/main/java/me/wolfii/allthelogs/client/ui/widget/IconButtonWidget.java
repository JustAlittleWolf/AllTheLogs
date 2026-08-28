package me.wolfii.allthelogs.client.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 20×20 vanilla-style button that draws a 16×16 GUI sprite, matching ScreenshotViewer's icon buttons.
 */
public final class IconButtonWidget extends Button {
    private static final int ICON_SIZE = 16;
    private static final WidgetSprites BACKGROUND = new WidgetSprites(
        Identifier.withDefaultNamespace("widget/button"),
        Identifier.withDefaultNamespace("widget/button_disabled"),
        Identifier.withDefaultNamespace("widget/button_highlighted"));

    private final Identifier iconTexture;

    public IconButtonWidget(
        int x,
        int y,
        int width,
        int height,
        Component message,
        @Nullable Identifier iconTexture,
        OnPress pressAction
    ) {
        super(x, y, width, height, message, pressAction, DEFAULT_NARRATION);
        this.iconTexture = iconTexture;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            BACKGROUND.get(isActive(), isHoveredOrFocused()),
            getX(),
            getY(),
            getWidth(),
            getHeight(),
            getAlpha());
        if (iconTexture == null) return;
        int iconX = getX() + (getWidth() - ICON_SIZE) / 2;
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2;
        context.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            iconTexture,
            iconX,
            iconY,
            ICON_SIZE,
            ICON_SIZE,
            getAlpha());
    }
}
