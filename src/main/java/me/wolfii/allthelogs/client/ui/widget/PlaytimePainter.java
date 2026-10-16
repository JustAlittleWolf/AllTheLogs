package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.playtime.PlaytimeColors;
import me.wolfii.allthelogs.client.playtime.PlaytimeHover;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Drawing shared by the playtime charts. Bars are drawn in screen pixels, not GUI units, so every bar can
 * be exactly as wide as its neighbors and as narrow as one pixel.
 */
final class PlaytimePainter {
    static final float AXIS_SCALE = 0.75f;
    private static final int MAX_HOVER_LINES = 16;
    /** Narrowest bar, in screen pixels, that still gets hatching. */
    private static final int HATCH_MIN_WIDTH = 5;
    private static final int HATCH_PERIOD = 6;

    private PlaytimePainter() {
    }

    /**
     * Screen pixels per GUI unit.
     */
    static int scale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
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
     * Stacked sections drawn upward from {@code bottom}. Every series except {@code highlight} is dimmed while
     * one is highlighted. Empty sections are skipped so neighbors meet. Hatched series get diagonal stripes
     * when the bar is wide enough to show them. Coordinates are screen pixels.
     */
    static void stacked(OwoUIGraphics graphics, int left, int right, int bottom, int height, double axisMax,
                        double[] display, List<PlaytimeSeries> series, int highlight) {
        if (right <= left || height <= 0 || axisMax <= 0) return;
        double cursor = 0;
        int baseline = bottom;
        for (int index = 0; index < display.length; index++) {
            if (display[index] <= 0) continue;
            cursor += display[index];
            int top = bottom - (int) Math.round(cursor / axisMax * height);
            if (top < baseline && index < series.size()) {
                PlaytimeSeries entry = series.get(index);
                int color = highlight >= 0 && highlight != index ? PlaytimeColors.dim(entry.color()) : entry.color();
                graphics.fill(left, top, right, baseline, color);
                if (entry.hatched() && right - left >= HATCH_MIN_WIDTH) {
                    hatch(graphics, left, top, right, baseline, PlaytimeColors.stripe(color));
                }
            }
            baseline = top;
        }
    }

    /**
     * Lines rising to the right across the rectangle, {@link #HATCH_PERIOD} pixels apart along each edge.
     */
    private static void hatch(OwoUIGraphics graphics, int left, int top, int right, int bottom, int argb) {
        Color color = Color.ofArgb(argb);
        int phase = Math.floorMod(left + bottom, HATCH_PERIOD);
        for (int sum = left + top - phase + HATCH_PERIOD; sum < right + bottom; sum += HATCH_PERIOD) {
            int fromX = Math.max(left, sum - bottom);
            int toX = Math.min(right, sum - top);
            if (toX - fromX < 1) continue;
            graphics.drawLine(fromX, sum - fromX, toX, sum - toX, 1.5, color);
        }
    }

    /**
     * Grid lines and labels for four steps from zero to {@code axisMax}.
     */
    static void yAxis(OwoUIGraphics graphics, int widgetY, int plotX, int plotY, int plotWidth, int plotHeight,
                      double axisMax, DoubleFunction<String> label) {
        Font font = font();
        for (int step = 0; step <= 3; step++) {
            double fraction = step / 3.0;
            int lineY = plotY + plotHeight - (int) Math.round(fraction * plotHeight);
            graphics.fill(plotX, lineY, plotX + plotWidth, lineY + 1, step == 0 ? Colors.TRACK_BORDER : Colors.SEPARATOR);
            String text = label.apply(axisMax * fraction);
            int textWidth = Math.round(font.width(text) * AXIS_SCALE);
            graphics.drawText(Component.literal(text), plotX - 4 - textWidth, Math.max(widgetY, lineY - font.lineHeight),
                AXIS_SCALE, Colors.TICK_LABEL);
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
     * Hover card kept inside the widget bounds, below the pointer or above it when there is no room.
     */
    static void hoverCard(OwoUIGraphics graphics, List<PlaytimeHover.Line> lines, int mouseX, int mouseY,
                          int x, int y, int width, int height) {
        if (lines.isEmpty()) return;
        int hidden = Math.max(0, lines.size() - MAX_HOVER_LINES);
        int shown = lines.size() - hidden;
        Font font = font();
        int textWidth = 0;
        for (int index = 0; index < shown; index++) {
            textWidth = Math.max(textWidth, font.width(lines.get(index).text()));
        }
        String more = null;
        if (hidden > 0) {
            more = Component.translatable("allthelogs.playtime.hover.more", hidden).getString();
            textWidth = Math.max(textWidth, font.width(more));
        }
        int rows = shown + (more == null ? 0 : 1);
        int pad = 4;
        int boxWidth = Math.min(width - 4, textWidth + pad * 2);
        int boxHeight = pad * 2 + rows * font.lineHeight;
        int boxX = Math.max(x + 2, Math.min(mouseX + 8, x + width - boxWidth - 2));
        int boxY = mouseY + 12;
        if (boxY + boxHeight > y + height) boxY = Math.max(y + 2, mouseY - boxHeight - 4);
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, Colors.HOVER_CHIP);
        int textY = boxY + pad;
        for (int index = 0; index < shown; index++) {
            PlaytimeHover.Line line = lines.get(index);
            graphics.drawText(Component.literal(line.text()), boxX + pad, textY, 1f, line.color());
            textY += font.lineHeight;
        }
        if (more != null) graphics.drawText(Component.literal(more), boxX + pad, textY, 1f, Colors.MUTED);
    }

    static Font font() {
        return Minecraft.getInstance().font;
    }
}
