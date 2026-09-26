package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.mixin.EditBoxAccessor;
import me.wolfii.allthelogs.client.search.SearchDecorations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Search box whose regex {@code /} and {@code /i} are drawn beside the text instead of stored in it.
 * Cursor, selection, copy, and paste see only the pattern.
 */
public final class DecoratedSearchField extends TextBoxComponent {
    private String prefix = "";
    private String suffix = "";

    public DecoratedSearchField() {
        super(Sizing.expand());
    }

    public void setDecorations(String prefix, String suffix) {
        String nextPrefix = prefix == null ? "" : prefix;
        String nextSuffix = suffix == null ? "" : suffix;
        if (nextPrefix.equals(this.prefix) && nextSuffix.equals(this.suffix)) return;
        this.prefix = nextPrefix;
        this.suffix = nextSuffix;
        applyTextInset();
        if (!getValue().isEmpty()) {
            setCursorPosition(getCursorPosition());
        }
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        applyTextInset();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        applyTextInset();
    }

    @Override
    public void updateX(int x) {
        super.updateX(x);
        applyTextInset();
    }

    @Override
    public void updateY(int y) {
        super.updateY(y);
        applyTextInset();
    }

    @Override
    public int getInnerWidth() {
        return Math.max(1, super.getInnerWidth() - leadingInset() - trailingInset());
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        applyTextInset();
        super.onClick(event, doubled);
    }

    @Override
    public void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
        applyTextInset();
        super.onDrag(event, deltaX, deltaY);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        applyTextInset();
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        Font font = Minecraft.getInstance().font;
        if (font == null) return;
        int textY = textY();
        if (!prefix.isEmpty()) {
            drawDecoration(graphics, font, prefix, getX() + horizontalPadding(), textY);
        }
        if (!suffix.isEmpty()) {
            int x = getX() + getWidth() - horizontalPadding() - font.width(suffix);
            drawDecoration(graphics, font, suffix, x, textY);
        }
    }

    private void applyTextInset() {
        // Cast through EditBox: this class is final, so a direct cast to a mixin accessor does not compile.
        // The accessors are mixed onto EditBox and are therefore implemented by every subclass at runtime.
        EditBox box = this;
        ((io.wispforest.owo.mixin.ui.access.EditBoxAccessor) box).owo$updateTextPosition();
        int leading = leadingInset();
        if (leading == 0) return;
        EditBoxAccessor access = (EditBoxAccessor) box;
        access.allthelogs$setTextX(access.allthelogs$getTextX() + leading);
    }

    private int leadingInset() {
        return inset(prefix);
    }

    private int trailingInset() {
        return inset(suffix);
    }

    private int inset(String decoration) {
        if (decoration == null || decoration.isEmpty()) return 0;
        Font font = Minecraft.getInstance().font;
        if (font == null) return 0;
        return font.width(decoration) + SearchDecorations.GAP;
    }

    private int horizontalPadding() {
        return isBordered() ? 4 : 0;
    }

    private int textY() {
        if (!isBordered()) return getY();
        return getY() + (getHeight() - 8) / 2;
    }

    private static void drawDecoration(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        int drawn = 0;
        for (int i = 0; i < text.length(); i++) {
            String glyph = text.substring(i, i + 1);
            int color = SearchDecorations.decorationColor(text, i);
            graphics.text(font, glyph, x + drawn, y, color, false);
            drawn += font.width(glyph);
        }
    }
}
