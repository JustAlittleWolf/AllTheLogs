package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.OwoUIGraphics;

/**
 * Stacked rectangles drawn upward from a shared baseline. Empty sections are skipped so neighbors meet.
 */
final class PlaytimeBars {
    private PlaytimeBars() {
    }

    static void stacked(OwoUIGraphics graphics, int left, int right, int bottom, int height, double axisMax,
                        double[] display, int[] colors) {
        if (right <= left || height <= 0 || axisMax <= 0) return;
        double cursor = 0;
        int baseline = bottom;
        for (int index = 0; index < display.length; index++) {
            if (display[index] <= 0) continue;
            cursor += display[index];
            int top = bottom - (int) Math.round(cursor / axisMax * height);
            if (top < baseline) {
                graphics.fill(left, top, right, baseline, colors[index]);
            }
            baseline = top;
        }
    }

    static void outline(OwoUIGraphics graphics, int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) return;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }
}
