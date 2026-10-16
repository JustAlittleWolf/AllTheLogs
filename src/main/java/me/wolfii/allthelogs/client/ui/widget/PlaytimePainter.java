package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.playtime.PlaytimeColors;
import me.wolfii.allthelogs.client.playtime.PlaytimeHover;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Drawing shared by the playtime charts. Bars are painted in screen pixels into a {@link PixelCanvas}, so
 * every pixel column can hold its own bar and patterns line up across neighboring bars.
 */
final class PlaytimePainter {
    static final float AXIS_SCALE = 0.75f;
    /** Translucent band over the bar or day under the pointer. */
    static final int HOVER_BAND = 0x30FFFFFF;
    private static final int AXIS_STEPS = 3;
    private static final int AXIS_GAP = 4;
    private static final int VALUE_GAP = 12;
    private static final int MAX_HOVER_LINES = 16;

    private PlaytimePainter() {
    }

    /**
     * Screen pixels per GUI unit.
     */
    static int scale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /**
     * Pattern cell size in screen pixels, so patterns look the same at every GUI scale.
     */
    static int patternUnit(int scale) {
        return Math.max(1, (scale + 1) / 2);
    }

    /**
     * Runs {@code paint} with coordinates in screen pixels.
     */
    static void inPixels(OwoUIGraphics graphics, int scale, Runnable paint) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(1f / scale, 1f / scale);
        try {
            paint.run();
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * Paints one stacked bar upward from {@code bottom} into {@code canvas}. Every series except
     * {@code highlight} is dimmed while one is highlighted. Empty sections are skipped so neighbors meet.
     */
    static void stack(PixelCanvas canvas, int left, int right, int bottom, int height, double axisMax,
                      double[] display, List<PlaytimeSeries> series, int highlight, int unit) {
        if (right <= left || height <= 0 || axisMax <= 0) return;
        double cursor = 0;
        int baseline = bottom;
        for (int index = 0; index < display.length && index < series.size(); index++) {
            if (display[index] <= 0) continue;
            cursor += display[index];
            int top = bottom - (int) Math.round(cursor / axisMax * height);
            if (top < baseline) {
                PlaytimeSeries entry = series.get(index);
                int color = highlight >= 0 && highlight != index ? PlaytimeColors.dim(entry.color()) : entry.color();
                canvas.fill(left, top, right, baseline, color, entry.pattern(), unit);
            }
            baseline = top;
        }
    }

    /**
     * Width the value labels of {@link #yAxis} need, including the gap to the plot.
     */
    static int yAxisWidth(double axisMax, DoubleFunction<String> label) {
        int widest = 0;
        for (int step = 0; step <= AXIS_STEPS; step++) {
            widest = Math.max(widest, axisWidth(label.apply(axisMax * step / AXIS_STEPS)));
        }
        return widest + AXIS_GAP + 2;
    }

    /**
     * Grid lines and value labels for four steps from zero to {@code axisMax}.
     */
    static void yAxis(OwoUIGraphics graphics, int widgetY, int plotX, int plotY, int plotWidth, int plotHeight,
                      double axisMax, DoubleFunction<String> label) {
        Font font = font();
        for (int step = 0; step <= AXIS_STEPS; step++) {
            double fraction = step / (double) AXIS_STEPS;
            int lineY = plotY + plotHeight - (int) Math.round(fraction * plotHeight);
            graphics.fill(plotX, lineY, plotX + plotWidth, lineY + 1, step == 0 ? Colors.TRACK_BORDER : Colors.SEPARATOR);
            String text = label.apply(axisMax * fraction);
            graphics.drawText(Component.literal(text), plotX - AXIS_GAP - axisWidth(text),
                Math.max(widgetY, lineY - font.lineHeight), AXIS_SCALE, Colors.TICK_LABEL);
        }
    }

    static void axisLabel(OwoUIGraphics graphics, String text, int x, int y) {
        graphics.drawText(Component.literal(text), x, y, AXIS_SCALE, Colors.TICK_LABEL);
    }

    static int axisWidth(String text) {
        return Math.round(font().width(text) * AXIS_SCALE);
    }

    static void centered(OwoUIGraphics graphics, Component text, int x, int y, int width, int height) {
        Font font = font();
        int textWidth = font.width(text);
        graphics.drawText(text, x + Math.max(0, (width - textWidth) / 2), y + Math.max(0, (height - font.lineHeight) / 2),
            1f, Colors.MUTED);
    }

    /**
     * Hover card kept inside the given bounds, below the pointer or above it when there is no room. Labels
     * are left-aligned and values right-aligned in a second column.
     */
    static void hoverCard(OwoUIGraphics graphics, List<PlaytimeHover.Line> lines, int mouseX, int mouseY,
                          int x, int y, int width, int height) {
        if (lines.isEmpty()) return;
        int hidden = Math.max(0, lines.size() - MAX_HOVER_LINES);
        int shown = lines.size() - hidden;
        Font font = font();
        int labelWidth = 0;
        int valueWidth = 0;
        for (int index = 0; index < shown; index++) {
            PlaytimeHover.Line line = lines.get(index);
            labelWidth = Math.max(labelWidth, font.width(text(line.label(), line.bold())));
            valueWidth = Math.max(valueWidth, font.width(line.value()));
        }
        String more = null;
        if (hidden > 0) {
            more = Component.translatable("allthelogs.playtime.hover.more", hidden).getString();
            labelWidth = Math.max(labelWidth, font.width(more));
        }
        int rows = shown + (more == null ? 0 : 1);
        int pad = 4;
        int contentWidth = labelWidth + (valueWidth > 0 ? VALUE_GAP + valueWidth : 0);
        int boxWidth = Math.min(width - 4, contentWidth + pad * 2);
        int boxHeight = pad * 2 + rows * font.lineHeight;
        int boxX = Math.max(x + 2, Math.min(mouseX + 8, x + width - boxWidth - 2));
        int boxY = mouseY + 12;
        if (boxY + boxHeight > y + height) boxY = Math.max(y + 2, mouseY - boxHeight - 4);
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, Colors.HOVER_CHIP);
        int textY = boxY + pad;
        int valueRight = boxX + boxWidth - pad;
        for (int index = 0; index < shown; index++) {
            PlaytimeHover.Line line = lines.get(index);
            graphics.drawText(text(line.label(), line.bold()), boxX + pad, textY, 1f, line.color());
            if (!line.value().isEmpty()) {
                graphics.drawText(Component.literal(line.value()), valueRight - font.width(line.value()), textY, 1f,
                    Colors.MUTED);
            }
            textY += font.lineHeight;
        }
        if (more != null) graphics.drawText(Component.literal(more), boxX + pad, textY, 1f, Colors.MUTED);
    }

    private static Component text(String text, boolean bold) {
        return bold ? Component.literal(text).withStyle(ChatFormatting.BOLD) : Component.literal(text);
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }
}
