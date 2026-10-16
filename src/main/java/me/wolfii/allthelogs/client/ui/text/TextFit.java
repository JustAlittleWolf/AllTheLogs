package me.wolfii.allthelogs.client.ui.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Cuts plain text to a pixel width with a trailing ellipsis.
 */
public final class TextFit {
    private static final String ELLIPSIS = "…";

    private TextFit() {
    }

    public static String fit(String text, int width) {
        Font font = Minecraft.getInstance().font;
        if (text == null || font.width(text) <= width) return text;
        int room = Math.max(0, width - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(text, room).stripTrailing() + ELLIPSIS;
    }
}
