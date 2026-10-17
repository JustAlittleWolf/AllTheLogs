package me.wolfii.allthelogs.client.ui.widget.playtime;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeHover;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import me.wolfii.allthelogs.client.search.ActiveFilters;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Weekday bars on the left and an hour-of-day heat map on the right, split by a divider. Both put the
 * days along the bottom: the bars run Monday to Sunday, and the heat map adds a summed "All" column, set
 * apart by its own divider, with the time of day running down each column as one continuous strip in the
 * chart's slot length. Hovering picks one slot. A small key centered above the heat map shows which end of
 * the color scale means more playtime. Weekday columns share one color
 * scale and the "All" column has its own. While a series is highlighted, the heat map counts only that
 * series. Both halves are painted into textures only when the chart, the size, or the highlight changes,
 * and they live in one component so the hover card can reach across them.
 */
public final class WeekdayChartWidget extends BaseUIComponent implements AutoCloseable {
    private static final int TOP = 16;
    private static final int SPLIT_GAP = 9;
    private static final int KEY_WIDTH = 40;
    private static final int KEY_HEIGHT = 4;
    private static final int HEAT_EMPTY = 0xFF1A1A1A;
    private static final int[] HEAT_STOPS = {0xFF2D1150, 0xFFB5367A, 0xFFFCA050};
    private static final int[] HOUR_STEPS = {1, 2, 3, 4, 6, 12};
    /** Light wash over the hovered hour of the heat map. */
    private static final int HOVER_CELL = 0x48FFFFFF;
    /** Dividers between the bar chart and the heat map, and before "All". Brighter and wider than grid lines. */
    private static final int DIVIDER = 0xFF6A6A6A;
    private static final int DIVIDER_WIDTH = 1;
    private static final int DASH = 3;

    private final SeriesHighlight highlight;
    private final PixelCanvas bars = new PixelCanvas();
    private final PixelCanvas heat = new PixelCanvas();
    private final PixelCanvas key = new PixelCanvas();
    private PlaytimeWeekday.Result chart;
    private Component placeholder = Component.empty();
    private BooleanSupplier hoverBlocked = () -> false;
    private PlaytimeWeekday.Result painted;
    private int paintedWidth;
    private int paintedHeight;
    private int paintedScale;
    private int paintedHighlight;
    private String cardKey;
    private List<PlaytimeHover.Line> card = List.of();

    public WeekdayChartWidget(SeriesHighlight highlight) {
        this.highlight = highlight;
        this.sizing(Sizing.fill(), Sizing.fill());
    }

    public void chart(PlaytimeWeekday.Result chart) {
        this.chart = chart;
    }

    public void placeholder(Component placeholder) {
        this.placeholder = placeholder == null ? Component.empty() : placeholder;
    }

    /**
     * While this is true the pointer neither highlights nor shows a hover card, e.g. while a menu is open.
     */
    public void hoverBlocked(BooleanSupplier hoverBlocked) {
        this.hoverBlocked = hoverBlocked;
    }

    /**
     * Releases the textures. Drawing again repaints them, so a screen that is shown again keeps working.
     */
    @Override
    public void close() {
        bars.close();
        heat.close();
        key.close();
        painted = null;
    }

    /**
     * Columns in screen pixels: {@code count} equally wide columns with a gap between them, centered in the
     * given width. Column {@code gapBefore} gets an extra gap in front of it, or none when negative.
     */
    private record Columns(int left, int slot, int bar, int gapBefore, int extra) {
        static Columns of(int pixelWidth, int count, int gapBefore, int scale) {
            int extra = gapBefore >= 0 ? 5 * scale : 0;
            int slot = Math.max(1, (pixelWidth - extra) / count);
            int gap = Math.max(scale, slot / 6);
            int bar = Math.max(1, slot - gap);
            int used = slot * count - gap + extra;
            return new Columns(Math.max(0, (pixelWidth - used) / 2), slot, bar, gapBefore, extra);
        }

        int barLeft(int column) {
            return left + column * slot + (gapBefore >= 0 && column >= gapBefore ? extra : 0);
        }

        int at(int pixelX, int count) {
            for (int column = 0; column < count; column++) {
                int start = barLeft(column);
                if (pixelX >= start && pixelX < start + bar) return column;
            }
            return -1;
        }
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(x, y, x + width, y + height, Colors.LIST_BACKGROUND);
        if (chart == null || chart.isEmpty()) {
            highlight.chart(-1);
            PlaytimePainter.centered(graphics, placeholder, x, y, width, height);
            return;
        }
        int scale = PlaytimePainter.scale();
        boolean pointer = !hoverBlocked.getAsBoolean() && isInBoundingBox(mouseX, mouseY);
        int pixelX = mouseX * scale + scale / 2;
        int pixelY = mouseY * scale + scale / 2;

        int barsWidth = Math.max(110, width * 2 / 5);
        int barsAxis = Math.max(16, PlaytimePainter.yAxisWidth(chart.axisMax(), this::durationLabel));
        PlaytimeChartModel.Plot barPlot = PlaytimeChartModel.plot(barsWidth, height, barsAxis, TOP);
        Columns days = Columns.of(barPlot.width() * scale, 7, -1, scale);
        int barsLeft = (x + barPlot.x()) * scale;
        int plotTop = (y + barPlot.y()) * scale;
        int plotHeight = barPlot.height() * scale;

        int heatX = x + barsWidth + SPLIT_GAP;
        int heatAxis = PlaytimePainter.axisWidth("00:00") + 4;
        int heatGuiWidth = Math.max(1, x + width - 2 - heatX - heatAxis);
        Columns heatDays = Columns.of(heatGuiWidth * scale, PlaytimeWeekday.ALL_DAYS + 1, PlaytimeWeekday.ALL_DAYS, scale);
        int heatLeft = (heatX + heatAxis) * scale;

        int hoveredDay = -1;
        int hoveredSeries = -1;
        int heatColumn = -1;
        int heatSlot = -1;
        int slots = chart.slots();
        if (pointer && pixelY >= plotTop && pixelY < plotTop + plotHeight) {
            hoveredDay = days.at(pixelX - barsLeft, 7);
            if (hoveredDay >= 0) {
                hoveredSeries = PlaytimeChartModel.seriesAt(chart.bars().get(hoveredDay).display(), chart.axisMax(),
                    mouseY - y, barPlot.y(), barPlot.height());
            }
            heatColumn = heatDays.at(pixelX - heatLeft, PlaytimeWeekday.ALL_DAYS + 1);
            if (heatColumn >= 0) heatSlot = Math.min(slots - 1, (pixelY - plotTop) * slots / plotHeight);
        }
        highlight.chart(hoveredSeries);
        int active = highlight.active();
        if (painted != chart || paintedWidth != width || paintedHeight != height || paintedScale != scale
            || paintedHighlight != active) {
            paintBars(days, barPlot.width() * scale, plotHeight, active, scale);
            paintHeat(heatDays, heatGuiWidth * scale, plotHeight, active);
            paintKey(scale);
            painted = chart;
            paintedWidth = width;
            paintedHeight = height;
            paintedScale = scale;
            paintedHighlight = active;
            cardKey = null;
        }

        PlaytimePainter.yAxis(graphics, y, x + barPlot.x(), y + barPlot.y(), barPlot.width(), barPlot.height(),
            chart.axisMax(), this::durationLabel);
        bars.draw(graphics, scale, barsLeft, plotTop);
        heat.draw(graphics, scale, heatLeft, plotTop);
        drawHourAxis(graphics, heatX + heatAxis, y + barPlot.y(), heatGuiWidth, barPlot.height());
        int heatStart = heatLeft + heatDays.barLeft(0);
        int heatEnd = heatLeft + heatDays.barLeft(PlaytimeWeekday.ALL_DAYS) + heatDays.bar();
        drawKey(graphics, scale, (heatStart + heatEnd) / 2 / scale, y + 3);

        int labelY = y + barPlot.y() + barPlot.height() + 3;
        int separatorBottom = labelY + 7;
        int split = x + barsWidth + SPLIT_GAP / 2;
        for (int dash = y + 2; dash < separatorBottom; dash += DASH * 2) {
            graphics.fill(split, dash, split + DIVIDER_WIDTH, Math.min(dash + DASH, separatorBottom), DIVIDER);
        }
        int allLeft = heatLeft + heatDays.barLeft(PlaytimeWeekday.ALL_DAYS);
        int sundayEnd = heatLeft + heatDays.barLeft(6) + heatDays.bar();
        int allLine = (sundayEnd + allLeft) / 2 / scale - DIVIDER_WIDTH / 2;
        graphics.fill(allLine, y + barPlot.y(), allLine + DIVIDER_WIDTH, y + barPlot.y() + barPlot.height(), DIVIDER);

        for (int day = 0; day < 7; day++) {
            dayLabel(graphics, day, barsLeft + days.barLeft(day), days.bar(), days.slot(), scale, labelY);
            dayLabel(graphics, day, heatLeft + heatDays.barLeft(day), heatDays.bar(), heatDays.slot(), scale, labelY);
        }
        dayLabel(graphics, PlaytimeWeekday.ALL_DAYS, allLeft, heatDays.bar(), heatDays.slot(), scale, labelY);

        List<PlaytimeHover.Line> lines = null;
        if (hoveredDay >= 0) {
            int left = barsLeft + days.barLeft(hoveredDay);
            PlaytimePainter.inPixels(graphics, scale, () ->
                graphics.fill(left, plotTop, left + days.bar(), plotTop + plotHeight, PlaytimePainter.HOVER_BAND));
            int day = hoveredDay;
            int focus = hoveredSeries;
            lines = card("day:" + day + ":" + focus, () -> chart.dayHover(day, totalLabel(), unknown(), focus));
        } else if (heatSlot >= 0) {
            int cellLeft = heatLeft + heatDays.barLeft(heatColumn);
            int cellTop = plotTop + heatSlot * plotHeight / slots;
            int cellBottom = Math.max(cellTop + 1, plotTop + (heatSlot + 1) * plotHeight / slots);
            PlaytimePainter.inPixels(graphics, scale, () ->
                graphics.fill(cellLeft, cellTop, cellLeft + heatDays.bar(), cellBottom, HOVER_CELL));
            int column = heatColumn;
            int slot = heatSlot;
            lines = card("heat:" + column + ":" + slot, () -> chart.heatHover(column, slot,
                dayName(column) + "  " + chart.slotLabel(slot), totalLabel(), unknown(), -1));
        }
        if (lines != null) PlaytimePainter.hoverCard(graphics, lines, mouseX, mouseY, x, y, width, height, true);
    }

    private List<PlaytimeHover.Line> card(String key, Supplier<List<PlaytimeHover.Line>> build) {
        if (!key.equals(cardKey)) {
            cardKey = key;
            card = build.get();
        }
        return card;
    }

    private void paintBars(Columns days, int width, int height, int active, int scale) {
        bars.reset(width, height);
        int unit = PlaytimePainter.patternUnit(scale);
        for (int day = 0; day < 7; day++) {
            int left = days.barLeft(day);
            PlaytimePainter.stack(bars, left, left + days.bar(), height, height, chart.axisMax(),
                chart.bars().get(day).display(), chart.series(), active, unit);
        }
        bars.upload();
    }

    /**
     * Paints each pixel row with the slot it shows, or the average of the slots it covers when slots are
     * shorter than a pixel. Weekday columns share one color scale and "All" has its own.
     */
    private void paintHeat(Columns columns, int width, int height, int active) {
        heat.reset(width, height);
        int series = active >= 0 && active < chart.series().size() ? active : -1;
        int slots = chart.slots();
        double[][] values = new double[PlaytimeWeekday.ALL_DAYS + 1][height];
        double dayMax = 0;
        double allMax = 0;
        for (int column = 0; column <= PlaytimeWeekday.ALL_DAYS; column++) {
            for (int row = 0; row < height; row++) {
                int from = row * slots / height;
                int to = Math.max(from + 1, (row + 1) * slots / height);
                long sum = 0;
                for (int slot = from; slot < to && slot < slots; slot++) sum += chart.heatMillis(column, slot, series);
                double value = sum / (double) (to - from);
                values[column][row] = value;
                if (column < PlaytimeWeekday.ALL_DAYS) dayMax = Math.max(dayMax, value);
                else allMax = Math.max(allMax, value);
            }
        }
        for (int column = 0; column <= PlaytimeWeekday.ALL_DAYS; column++) {
            int left = columns.barLeft(column);
            double max = column == PlaytimeWeekday.ALL_DAYS ? allMax : dayMax;
            for (int row = 0; row < height; row++) {
                heat.fill(left, row, left + columns.bar(), row + 1, shade(values[column][row], max));
            }
        }
        heat.upload();
    }

    /**
     * The color scale from no playtime to the most, drawn as a small strip above the heat map.
     */
    private void paintKey(int scale) {
        int width = KEY_WIDTH * scale;
        key.reset(width, KEY_HEIGHT * scale);
        for (int pixel = 0; pixel < width; pixel++) {
            long value = pixel == 0 ? 0 : pixel;
            key.fill(pixel, 0, pixel + 1, KEY_HEIGHT * scale, shade(value, width - 1));
        }
        key.upload();
    }

    /**
     * "Less", the color strip, and "More", centered on {@code center}.
     */
    private void drawKey(OwoUIGraphics graphics, int scale, int center, int top) {
        String less = Component.translatable("allthelogs.playtime.heat.less").getString();
        String more = Component.translatable("allthelogs.playtime.heat.more").getString();
        int lessWidth = PlaytimePainter.axisWidth(less);
        int total = lessWidth + 3 + KEY_WIDTH + 3 + PlaytimePainter.axisWidth(more);
        int lessX = center - total / 2;
        int keyX = lessX + lessWidth + 3;
        int moreX = keyX + KEY_WIDTH + 3;
        PlaytimePainter.axisLabel(graphics, less, lessX, top);
        key.draw(graphics, scale, keyX * scale, (top + 1) * scale);
        PlaytimePainter.axisLabel(graphics, more, moreX, top);
    }

    /**
     * Hour labels left of the heat map with faint lines across it, like the value grid of a bar chart.
     */
    private void drawHourAxis(OwoUIGraphics graphics, int left, int top, int width, int height) {
        int step = HOUR_STEPS[HOUR_STEPS.length - 1];
        for (int candidate : HOUR_STEPS) {
            if (height * candidate / PlaytimeWeekday.HOURS >= 10) {
                step = candidate;
                break;
            }
        }
        for (int hour = 0; hour <= PlaytimeWeekday.HOURS; hour += step) {
            int lineY = top + hour * height / PlaytimeWeekday.HOURS;
            if (hour > 0 && hour < PlaytimeWeekday.HOURS) graphics.fill(left, lineY, left + width, lineY + 1, 0x40000000);
            String label = String.format("%02d:00", hour);
            PlaytimePainter.axisLabel(graphics, label, left - 3 - PlaytimePainter.axisWidth(label), lineY - 3);
        }
    }

    /**
     * Day name centered under a column, cut to two letters or one when the column's slot is too narrow.
     */
    private static void dayLabel(OwoUIGraphics graphics, int day, int pixelLeft, int pixelBar, int pixelSlot,
                                 int scale, int y) {
        String label = day == PlaytimeWeekday.ALL_DAYS
            ? Component.translatable("allthelogs.playtime.heat.all").getString()
            : PlaytimeWeekday.shortDayName(day);
        int room = pixelSlot / scale - 1;
        while (label.length() > 1 && PlaytimePainter.axisWidth(label) > room) {
            label = label.substring(0, label.length() - 1);
        }
        float center = (pixelLeft + pixelBar / 2f) / scale;
        PlaytimePainter.axisLabel(graphics, label, Math.round(center - PlaytimePainter.axisWidth(label) / 2f), y);
    }

    private static String dayName(int column) {
        return column == PlaytimeWeekday.ALL_DAYS
            ? Component.translatable("allthelogs.playtime.heat.all").getString()
            : PlaytimeWeekday.dayName(column);
    }

    private static String totalLabel() {
        return Component.translatable("allthelogs.playtime.hover.total").getString();
    }

    private static String unknown() {
        return ActiveFilters.name(null);
    }

    private String durationLabel(double value) {
        return PlaytimeDuration.axis(Math.round(value));
    }

    /**
     * Purple through magenta to orange as playtime grows. Empty hours stay dark.
     */
    private static int shade(double value, double max) {
        if (value <= 0 || max <= 0) return HEAT_EMPTY;
        double t = Math.pow(value / max, 0.6) * (HEAT_STOPS.length - 1);
        int stop = Math.min(HEAT_STOPS.length - 2, (int) t);
        double within = t - stop;
        int from = HEAT_STOPS[stop];
        int to = HEAT_STOPS[stop + 1];
        return 0xFF000000
            | (mix(from >> 16, to >> 16, within) << 16)
            | (mix(from >> 8, to >> 8, within) << 8)
            | mix(from, to, within);
    }

    private static int mix(int from, int to, double t) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return (int) Math.round(a + (b - a) * t);
    }
}
