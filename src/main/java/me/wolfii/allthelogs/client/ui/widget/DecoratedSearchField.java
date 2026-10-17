package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.mixin.EditBoxAccessor;
import me.wolfii.allthelogs.client.search.SearchDecorations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Search box whose regex {@code /} and {@code /i} are drawn beside the text instead of stored in it.
 * Cursor, selection, copy, and paste see only the pattern.
 */
public final class DecoratedSearchField extends TextBoxComponent {
    /** Pixels between the field and a tooltip anchored to it. */
    private static final int TOOLTIP_GAP = 4;
    private static final int TOOLTIP_MARGIN = 2;

    private String prefix = "";
    private String suffix = "";
    private Component hint = Component.empty();

    public DecoratedSearchField() {
        super(Sizing.expand());
    }

    /**
     * The box scrolled to the end of {@code value} while it had no room, so the field draws nothing.
     * {@code displayPos} is the first visible character. It only lands on the length when the inner width
     * fitted zero characters, which is what happens if the restored query is applied before layout.
     */
    static boolean hidesText(String value, int displayPos) {
        return value != null && !value.isEmpty() && displayPos >= value.length();
    }

    /**
     * Screen position of a tooltip for this field. It sits just above the field, near the pointer, so it
     * does not cover the pattern. It drops below the field when the top of the screen has no room, and
     * shifts left when it would run off the right.
     */
    static Vector2ic placeTooltip(int fieldY, int fieldHeight, int mouseX, int screenWidth, int screenHeight,
                                  int tooltipWidth, int tooltipHeight) {
        int x = mouseX;
        if (x + tooltipWidth > screenWidth - TOOLTIP_MARGIN) {
            x = screenWidth - tooltipWidth - TOOLTIP_MARGIN;
        }
        if (x < TOOLTIP_MARGIN) x = TOOLTIP_MARGIN;
        int above = fieldY - tooltipHeight - TOOLTIP_GAP;
        int y = above >= TOOLTIP_MARGIN ? above : fieldY + fieldHeight + TOOLTIP_GAP;
        if (y + tooltipHeight > screenHeight - TOOLTIP_MARGIN) {
            y = Math.max(TOOLTIP_MARGIN, screenHeight - tooltipHeight - TOOLTIP_MARGIN);
        }
        return new Vector2i(x, y);
    }

    /**
     * Screen x of the closing {@code /} or {@code /i}. It sits {@code gap} pixels after the visible query.
     * Returns {@code -1} when the end of the query is scrolled out of the box, so the mark is not drawn
     * in the middle of the pattern.
     */
    static int suffixX(int textX, int visibleWidth, int gap, int valueLength, int displayPos, int visibleChars) {
        if (displayPos + Math.max(0, visibleChars) < valueLength) return -1;
        return textX + Math.max(0, visibleWidth) + gap;
    }

    /**
     * Scrolls the stored query into the visible window after the box has a real width.
     */
    public void revealValue() {
        String value = getValue();
        if (value.isEmpty() || getWidth() <= 0) return;
        EditBoxAccessor access = (EditBoxAccessor) (EditBox) this;
        if (!hidesText(value, access.allthelogs$getDisplayPos())) return;
        int cursor = Math.min(getCursorPosition(), value.length());
        setCursorPosition(0);
        setCursorPosition(cursor);
    }

    @Override
    public void setHint(Component hint) {
        this.hint = hint == null ? Component.empty() : hint;
        super.setHint(this.hint);
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
    public void drawTooltip(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (!shouldDrawTooltip(mouseX, mouseY)) return;
        Font font = Minecraft.getInstance().font;
        if (font == null || tooltip() == null || tooltip().isEmpty()) return;
        graphics.tooltip(font, tooltip(), mouseX, mouseY, (screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight) ->
            placeTooltip(getY(), getHeight(), x, screenWidth, screenHeight, tooltipWidth, tooltipHeight), null);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        revealIfHidden();
        applyTextInset();
        boolean moveHint = hintFollowsSuffix();
        if (moveHint) super.setHint(Component.empty());
        try {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        } finally {
            if (moveHint) super.setHint(hint);
        }
        Font font = Minecraft.getInstance().font;
        if (font == null) return;
        int textY = textY();
        if (!prefix.isEmpty()) {
            drawDecoration(graphics, font, prefix, getX() + horizontalPadding(), textY);
        }
        int suffixAt = suffixX(font);
        if (suffixAt >= 0) {
            drawDecoration(graphics, font, suffix, suffixAt, textY);
        }
        if (moveHint && suffixAt >= 0) {
            int hintX = suffixAt + font.width(suffix) + SearchDecorations.GAP;
            graphics.text(font, hint, hintX, textY, 0xFF000000 | TextColor.DARK_GRAY.getValue());
        }
    }

    private void revealIfHidden() {
        EditBoxAccessor access = (EditBoxAccessor) (EditBox) this;
        if (!hidesText(getValue(), access.allthelogs$getDisplayPos())) return;
        if (getInnerWidth() <= 4) return;
        access.allthelogs$setDisplayPos(0);
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

    private boolean hintFollowsSuffix() {
        return !suffix.isEmpty() && getValue().isEmpty() && !isFocused() && !hint.getString().isEmpty();
    }

    /**
     * Closing mark, or {@code -1} when it should stay hidden. The editable text already starts after the
     * opening slash, so this is that text's right edge plus the padding.
     */
    private int suffixX(Font font) {
        if (suffix.isEmpty()) return -1;
        EditBoxAccessor access = (EditBoxAccessor) (EditBox) this;
        String value = getValue();
        int displayPos = Math.min(Math.max(0, access.allthelogs$getDisplayPos()), value.length());
        String visible = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        return suffixX(access.allthelogs$getTextX(), font.width(visible), SearchDecorations.GAP,
            value.length(), displayPos, visible.length());
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
