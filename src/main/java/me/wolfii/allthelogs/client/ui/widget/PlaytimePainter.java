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
        return Math.max(1, scale);
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
        stack(canvas, left, right, bottom, height, axisMax, display, series, highlight, unit, 0);
    }

    /**
     * @param patternShift horizontal scroll in screen pixels, so patterns move with the bars when panning
     */
    static void stack(PixelCanvas canvas, int left, int right, int bottom, int height, double axisMax,
                      double[] display, List<PlaytimeSeries> series, int highlight, int unit, int patternShift) {
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
                canvas.fill(left, top, right, baseline, color, entry.pattern(), unit, patternShift);
            }
            baseline = top;
        }
    }

    /**
     * Width the value labels of {@link #yAxis} need, including the gap to the plot.
     */
    static int yAxisWidth(double axisMax, DoubleFunction<String> label) {
        int widest = 0;
        for (int step = 1; step <= AXIS_STEPS; step++) {
            for (String line : label.apply(axisMax * step / AXIS_STEPS).split("\n")) {
                widest = Math.max(widest, axisWidth(line));
            }
        }
        return widest + AXIS_GAP + 2;
    }

    /**
     * The baseline and three grid lines up to {@code axisMax}, each with its value centered on the line.
     * A label may have several lines separated by {@code \n}; they are centered on each other.
     */
    static void yAxis(OwoUIGraphics graphics, int widgetY, int plotX, int plotY, int plotWidth, int plotHeight,
                      double axisMax, DoubleFunction<String> label) {
        int lineHeight = Math.round(font().lineHeight * AXIS_SCALE);
        for (int step = 0; step <= AXIS_STEPS; step++) {
            double fraction = step / (double) AXIS_STEPS;
            int lineY = plotY + plotHeight - (int) Math.round(fraction * plotHeight);
            graphics.fill(plotX, lineY, plotX + plotWidth, lineY + 1, step == 0 ? Colors.TRACK_BORDER : Colors.SEPARATOR);
            if (step == 0) continue;
            String[] lines = label.apply(axisMax * fraction).split("\n");
            int blockWidth = 0;
            for (String line : lines) blockWidth = Math.max(blockWidth, axisWidth(line));
            int blockRight = plotX - AXIS_GAP;
            int textY = Math.max(widgetY, lineY - lines.length * lineHeight / 2);
            for (String line : lines) {
                int lineX = blockRight - blockWidth + (blockWidth - axisWidth(line)) / 2;
                graphics.drawText(Component.literal(line), lineX, textY, AXIS_SCALE, Colors.TICK_LABEL);
                textY += lineHeight;
            }
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
     * are left-aligned and values right-aligned in a second column. Lines without a value, like the period
     * heading, only need to fit the card; they do not push the value column to the right.
     */
    static void hoverCard(OwoUIGraphics graphics, List<PlaytimeHover.Line> lines, int mouseX, int mouseY,
                          int x, int y, int width, int height) {
        if (lines.isEmpty()) return;
        int shown = lines.size();
        Font font = font();
        int labelWidth = 0;
        int valueWidth = 0;
        int plainWidth = 0;
        for (int index = 0; index < shown; index++) {
            PlaytimeHover.Line line = lines.get(index);
            int lineWidth = font.width(text(line));
            if (line.value().isEmpty()) {
                plainWidth = Math.max(plainWidth, lineWidth);
            } else {
                labelWidth = Math.max(labelWidth, lineWidth);
                valueWidth = Math.max(valueWidth, font.width(line.value()));
            }
        }
        int rows = shown;
        int pad = 4;
        int columnsWidth = valueWidth > 0 ? labelWidth + VALUE_GAP + valueWidth : labelWidth;
        int contentWidth = Math.max(plainWidth, columnsWidth);
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
            graphics.drawText(text(line), boxX + pad, textY, 1f, line.color());
            if (!line.value().isEmpty()) {
                graphics.drawText(Component.literal(line.value()), valueRight - font.width(line.value()), textY, 1f,
                    Colors.MUTED);
            }
            textY += font.lineHeight;
        }
    }

    private static Component text(PlaytimeHover.Line line) {
        if (line.hidden() > 0) return Component.translatable("allthelogs.playtime.hover.more", line.hidden());
        Component text = Component.literal(line.label());
        return line.bold() ? text.copy().withStyle(ChatFormatting.BOLD) : text;
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }
}
