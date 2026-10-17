package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The invalid-regex message, drawn with the same chrome as the match-count chip. It is as wide as the
 * search field and stays up without a hover. The message stays on one line until it is wider than the field.
 */
public final class SearchErrorChip extends BaseUIComponent {
    /** Horizontal padding inside the chip, matching the match-count chip. */
    public static final int PAD_X = 8;
    /** Space between the chip and the search field. */
    public static final int GAP = 2;

    private Component message = Component.empty();
    private List<FormattedCharSequence> lines = List.of();

    public SearchErrorChip() {
        this.sizing(Sizing.fill(), Sizing.content());
    }

    public void message(Component message) {
        Component next = message == null ? Component.empty() : message;
        if (next.getString().equals(this.message.getString())) return;
        this.message = next;
        notifyParentIfMounted();
    }

    public boolean visible() {
        return !message.getString().isEmpty();
    }

    /**
     * Width the message can use before it wraps. The chip itself is the search field's width.
     */
    public static int innerWidth(int chipWidth) {
        return Math.max(1, chipWidth - PAD_X * 2);
    }

    /**
     * Whether {@code textWidth} needs a second line inside a chip of {@code chipWidth}.
     */
    public static boolean wraps(int textWidth, int chipWidth) {
        return textWidth > innerWidth(chipWidth);
    }

    /**
     * Chip height. A single line uses the match-count chip's height; further lines add a row each.
     */
    public static int heightFor(int lineCount, int lineHeight) {
        if (lineCount <= 0) return 0;
        if (lineCount == 1) return MessageListLayout.DATE_HEIGHT;
        return 4 + lineCount * lineHeight + 4;
    }

    /**
     * The message in {@link Colors#SEARCH_ERROR}, a red that is not fully saturated.
     */
    public static Component colored(Component message) {
        return message.copy().withColor(Colors.SEARCH_ERROR & 0xFFFFFF);
    }

    @Override
    public boolean canFocus(UIComponent.FocusSource source) {
        return false;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        refreshLines();
        Font font = font();
        int lineHeight = font == null ? 9 : font.lineHeight;
        return heightFor(lines.size(), lineHeight);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (lines.isEmpty() || height <= 0) return;
        Font font = font();
        if (font == null) return;
        HoverChip.fill(graphics, x, y, width, height, Colors.STATUS_CHIP);
        int textY = y + Math.max(0, (height - lines.size() * font.lineHeight) / 2);
        if (lines.size() == 1) textY += 2;
        for (FormattedCharSequence line : lines) {
            int textX = x + Math.max(PAD_X, (width - font.width(line)) / 2);
            graphics.text(font, line, textX, textY, Colors.SEARCH_ERROR, false);
            textY += font.lineHeight;
        }
    }

    private void refreshLines() {
        if (!visible()) {
            lines = List.of();
            return;
        }
        Font font = font();
        if (font == null) {
            lines = List.of();
            return;
        }
        lines = List.copyOf(font.split(colored(message), innerWidth(width)));
    }

    private static Font font() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.font;
    }
}
