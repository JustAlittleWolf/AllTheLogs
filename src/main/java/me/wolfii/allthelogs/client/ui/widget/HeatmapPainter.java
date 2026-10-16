package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.playtime.PlaytimeHover;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Hour-of-day heat map: one row per weekday and a summed row for all days underneath. Weekday rows share
 * one scale and the summed row has its own, so both use the full color range. While a series is
 * highlighted, only that series is counted.
 */
final class HeatmapPainter {
    private static final int EMPTY = 0xFF1A1A1A;
    private static final int LOW = 0xFF173A4A;
    private static final int HIGH = 0xFF8FDDFF;
    private static final int HOVER = 0xFFFFFFFF;
    private static final int[] HOUR_STEPS = {1, 2, 3, 4, 6, 12};
    private static final int TOP = 6;
    private static final int BOTTOM = 14;

    private HeatmapPainter() {
    }

    /**
     * Draws the map into the given area and returns the hover card for the cell under the pointer, or null.
     */
    static List<PlaytimeHover.Line> draw(OwoUIGraphics graphics, PlaytimeWeekday.Result chart, int highlight,
                                         int x, int y, int width, int height, int mouseX, int mouseY,
                                         boolean pointerInside) {
        String all = Component.translatable("allthelogs.playtime.heat.all").getString();
        int labelWidth = PlaytimePainter.axisWidth(all);
        for (int day = 0; day < 7; day++) {
            labelWidth = Math.max(labelWidth, PlaytimePainter.axisWidth(PlaytimeWeekday.shortDayName(day)));
        }
        int gridX = x + labelWidth + 4;
        int gridY = y + TOP;
        int gridWidth = width - labelWidth - 8;
        int gridHeight = height - TOP - BOTTOM;
        int scale = PlaytimePainter.scale();
        int gap = 1;
        int separator = 3 * scale;
        int cellWidth = (gridWidth * scale - (PlaytimeWeekday.HOURS - 1) * gap) / PlaytimeWeekday.HOURS;
        int cellHeight = (gridHeight * scale - 6 * gap - separator) / 8;
        if (cellWidth < 1 || cellHeight < 1) return null;
        int left = gridX * scale + (gridWidth * scale - cellWidth * PlaytimeWeekday.HOURS
            - (PlaytimeWeekday.HOURS - 1) * gap) / 2;
        int top = gridY * scale;

        long[][] values = new long[PlaytimeWeekday.ALL_DAYS + 1][PlaytimeWeekday.HOURS];
        long dayMax = 0;
        for (int row = 0; row <= PlaytimeWeekday.ALL_DAYS; row++) {
            for (int hour = 0; hour < PlaytimeWeekday.HOURS; hour++) {
                values[row][hour] = highlight >= 0 && highlight < chart.series().size()
                    ? chart.heat()[row][hour][highlight]
                    : chart.heatMillis(row, hour);
                if (row < PlaytimeWeekday.ALL_DAYS) dayMax = Math.max(dayMax, values[row][hour]);
            }
        }
        long allMax = 0;
        for (long value : values[PlaytimeWeekday.ALL_DAYS]) allMax = Math.max(allMax, value);
        long allDaysMax = allMax;
        long weekdayMax = dayMax;

        int hoveredRow = -1;
        int hoveredHour = -1;
        if (pointerInside) {
            int pixelX = mouseX * scale - left;
            int pixelY = mouseY * scale - top;
            int hour = Math.floorDiv(pixelX, cellWidth + gap);
            int row = rowAt(pixelY, cellHeight, gap, separator);
            if (hour >= 0 && hour < PlaytimeWeekday.HOURS && pixelX - hour * (cellWidth + gap) < cellWidth && row >= 0) {
                hoveredRow = row;
                hoveredHour = hour;
            }
        }
        int focusRow = hoveredRow;
        int focusHour = hoveredHour;
        PlaytimePainter.inPixels(graphics, scale, () -> {
            for (int row = 0; row <= PlaytimeWeekday.ALL_DAYS; row++) {
                int cellTop = top + rowTop(row, cellHeight, gap, separator);
                long max = row == PlaytimeWeekday.ALL_DAYS ? allDaysMax : weekdayMax;
                for (int hour = 0; hour < PlaytimeWeekday.HOURS; hour++) {
                    int cellLeft = left + hour * (cellWidth + gap);
                    if (row == focusRow && hour == focusHour) {
                        graphics.fill(cellLeft - gap, cellTop - gap, cellLeft + cellWidth + gap, cellTop + cellHeight + gap, HOVER);
                    }
                    graphics.fill(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight, shade(values[row][hour], max));
                }
            }
        });

        for (int row = 0; row <= PlaytimeWeekday.ALL_DAYS; row++) {
            String label = row == PlaytimeWeekday.ALL_DAYS ? all : PlaytimeWeekday.shortDayName(row);
            int center = (top + rowTop(row, cellHeight, gap, separator) + cellHeight / 2) / scale;
            PlaytimePainter.axisLabel(graphics, label, x + 1, center - 3);
        }
        int hourStep = HOUR_STEPS[HOUR_STEPS.length - 1];
        int labelRoom = PlaytimePainter.axisWidth("00") + 4;
        for (int step : HOUR_STEPS) {
            if ((cellWidth + gap) * step >= labelRoom * scale) {
                hourStep = step;
                break;
            }
        }
        int labelY = (top + rowTop(PlaytimeWeekday.ALL_DAYS, cellHeight, gap, separator) + cellHeight) / scale + 3;
        for (int hour = 0; hour < PlaytimeWeekday.HOURS; hour += hourStep) {
            String label = Integer.toString(hour);
            int center = (left + hour * (cellWidth + gap) + cellWidth / 2) / scale;
            PlaytimePainter.axisLabel(graphics, label, center - PlaytimePainter.axisWidth(label) / 2, labelY);
        }

        if (hoveredRow < 0) return null;
        String rowName = hoveredRow == PlaytimeWeekday.ALL_DAYS ? all : PlaytimeWeekday.dayName(hoveredRow);
        String heading = rowName + "  " + String.format("%02d:00 - %02d:00", hoveredHour, hoveredHour + 1);
        return chart.heatHover(hoveredRow, hoveredHour, heading,
            Component.translatable("allthelogs.playtime.hover.total").getString(),
            Component.translatable("allthelogs.playtime.server.unknown").getString());
    }

    private static int rowTop(int row, int cellHeight, int gap, int separator) {
        if (row == PlaytimeWeekday.ALL_DAYS) return 7 * cellHeight + 6 * gap + separator;
        return row * (cellHeight + gap);
    }

    private static int rowAt(int pixelY, int cellHeight, int gap, int separator) {
        if (pixelY < 0) return -1;
        int allTop = rowTop(PlaytimeWeekday.ALL_DAYS, cellHeight, gap, separator);
        if (pixelY >= allTop) return pixelY < allTop + cellHeight ? PlaytimeWeekday.ALL_DAYS : -1;
        int row = pixelY / (cellHeight + gap);
        return row < 7 && pixelY - row * (cellHeight + gap) < cellHeight ? row : -1;
    }

    private static int shade(long value, long max) {
        if (value <= 0 || max <= 0) return EMPTY;
        double t = Math.pow(value / (double) max, 0.6);
        return 0xFF000000
            | (mix(LOW >> 16, HIGH >> 16, t) << 16)
            | (mix(LOW >> 8, HIGH >> 8, t) << 8)
            | mix(LOW, HIGH, t);
    }

    private static int mix(int from, int to, double t) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return (int) Math.round(a + (b - a) * t);
    }
}
