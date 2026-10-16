package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeHover;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Weekday bars on the left and an hour-of-day heat map on the right. Both put the days along the bottom:
 * the bars run Monday to Sunday, and the heat map adds a summed "All" column with the hours running down
 * each column as one continuous strip. Weekday columns share one color scale and the "All" column has its
 * own. While a series is highlighted, the heat map counts only that series. Both halves are painted into
 * textures only when the chart, the size, or the highlight changes, and they live in one component so the
 * hover card can reach across them.
 */
public final class WeekdayChartWidget extends BaseUIComponent implements AutoCloseable {
    private static final int SPLIT_GAP = 10;
    private static final int HEAT_EMPTY = 0xFF1A1A1A;
    private static final int[] HEAT_STOPS = {0xFF2D1150, 0xFFB5367A, 0xFFFCA050};
    private static final int[] HOUR_STEPS = {1, 2, 3, 4, 6, 12};
    private static final int HOVER_OUTLINE = 0xFFFFFFFF;

    private final SeriesHighlight highlight;
    private final PixelCanvas bars = new PixelCanvas();
    private final PixelCanvas heat = new PixelCanvas();
    private PlaytimeWeekday.Result chart;
    private Component placeholder = Component.empty();
    private BooleanSupplier hoverBlocked = () -> false;
    private PlaytimeWeekday.Result painted;
    private int paintedWidth;
    private int paintedHeight;
    private int paintedScale;
    private int paintedHighlight;

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

    @Override
    public void close() {
        bars.close();
        heat.close();
    }

    /**
     * Columns in screen pixels: {@code count} equally wide columns with a gap between them, centered in the
     * given width. Column {@code gapBefore} gets an extra gap in front of it, or none when negative.
     */
    private record Columns(int left, int slot, int bar, int gapBefore, int extra) {
        static Columns of(int pixelWidth, int count, int gapBefore, int scale) {
            int extra = gapBefore >= 0 ? 3 * scale : 0;
            int slot = Math.max(1, (pixelWidth - extra) / count);
            int gap = Math.max(scale, slot / 5);
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

        int barsWidth = Math.max(140, width * 2 / 5);
        int barsAxis = Math.max(20, PlaytimePainter.yAxisWidth(chart.axisMax(), this::durationLabel));
        PlaytimeChartModel.Plot barPlot = PlaytimeChartModel.plot(barsWidth, height, barsAxis);
        Columns days = Columns.of(barPlot.width() * scale, 7, -1, scale);
        int barsLeft = (x + barPlot.x()) * scale;
        int plotTop = (y + barPlot.y()) * scale;
        int plotHeight = barPlot.height() * scale;

        int heatX = x + barsWidth + SPLIT_GAP;
        int heatAxis = PlaytimePainter.axisWidth("00:00") + 6;
        int heatGuiWidth = Math.max(1, x + width - PlaytimeChartModel.PLOT_RIGHT - heatX - heatAxis);
        Columns heatDays = Columns.of(heatGuiWidth * scale, PlaytimeWeekday.ALL_DAYS + 1, PlaytimeWeekday.ALL_DAYS, scale);
        int heatLeft = (heatX + heatAxis) * scale;

        int hoveredDay = -1;
        int hoveredSeries = -1;
        int heatColumn = -1;
        int heatHour = -1;
        if (pointer && pixelY >= plotTop && pixelY < plotTop + plotHeight) {
            hoveredDay = days.at(pixelX - barsLeft, 7);
            if (hoveredDay >= 0) {
                hoveredSeries = PlaytimeChartModel.seriesAt(chart.bars().get(hoveredDay).display(), chart.axisMax(),
                    mouseY - y, barPlot.y(), barPlot.height());
            }
            heatColumn = heatDays.at(pixelX - heatLeft, PlaytimeWeekday.ALL_DAYS + 1);
            if (heatColumn >= 0) {
                heatHour = Math.min(PlaytimeWeekday.HOURS - 1, (pixelY - plotTop) * PlaytimeWeekday.HOURS / plotHeight);
            }
        }
        highlight.chart(hoveredSeries);
        int active = highlight.active();
        if (painted != chart || paintedWidth != width || paintedHeight != height || paintedScale != scale
            || paintedHighlight != active) {
            paintBars(days, barPlot.width() * scale, plotHeight, active, scale);
            paintHeat(heatDays, heatGuiWidth * scale, plotHeight, active);
            painted = chart;
            paintedWidth = width;
            paintedHeight = height;
            paintedScale = scale;
            paintedHighlight = active;
        }

        PlaytimePainter.yAxis(graphics, y, x + barPlot.x(), y + barPlot.y(), barPlot.width(), barPlot.height(),
            chart.axisMax(), this::durationLabel);
        bars.draw(graphics, scale, barsLeft, plotTop);
        heat.draw(graphics, scale, heatLeft, plotTop);
        drawHourAxis(graphics, heatX + heatAxis, y + barPlot.y(), heatGuiWidth, barPlot.height());
        int labelY = y + barPlot.y() + barPlot.height() + 3;
        for (int day = 0; day < 7; day++) {
            String name = PlaytimeWeekday.shortDayName(day);
            dayLabel(graphics, name, barsLeft + days.barLeft(day), days.bar(), scale, labelY);
            dayLabel(graphics, name, heatLeft + heatDays.barLeft(day), heatDays.bar(), scale, labelY);
        }
        String all = Component.translatable("allthelogs.playtime.heat.all").getString();
        dayLabel(graphics, all, heatLeft + heatDays.barLeft(PlaytimeWeekday.ALL_DAYS), heatDays.bar(), scale, labelY);

        List<PlaytimeHover.Line> card = null;
        String unknown = Component.translatable("allthelogs.playtime.server.unknown").getString();
        if (hoveredDay >= 0) {
            int left = barsLeft + days.barLeft(hoveredDay);
            PlaytimePainter.inPixels(graphics, scale, () ->
                graphics.fill(left, plotTop, left + days.bar(), plotTop + plotHeight, PlaytimePainter.HOVER_BAND));
            card = chart.dayHover(hoveredDay, unknown);
        } else if (heatHour >= 0) {
            outlineCell(graphics, scale, heatLeft + heatDays.barLeft(heatColumn), heatDays.bar(),
                plotTop + heatHour * plotHeight / PlaytimeWeekday.HOURS,
                plotTop + (heatHour + 1) * plotHeight / PlaytimeWeekday.HOURS);
            String day = heatColumn == PlaytimeWeekday.ALL_DAYS ? all : PlaytimeWeekday.dayName(heatColumn);
            card = chart.heatHover(heatColumn, heatHour,
                day + "  " + String.format("%02d:00 - %02d:00", heatHour, heatHour + 1), unknown);
        }
        if (card != null) PlaytimePainter.hoverCard(graphics, card, mouseX, mouseY, x, y, width, height);
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

    private void paintHeat(Columns columns, int width, int height, int active) {
        heat.reset(width, height);
        long[][] values = new long[PlaytimeWeekday.ALL_DAYS + 1][PlaytimeWeekday.HOURS];
        long dayMax = 0;
        long allMax = 0;
        for (int column = 0; column <= PlaytimeWeekday.ALL_DAYS; column++) {
            for (int hour = 0; hour < PlaytimeWeekday.HOURS; hour++) {
                long value = active >= 0 && active < chart.series().size()
                    ? chart.heat()[column][hour][active]
                    : chart.heatMillis(column, hour);
                values[column][hour] = value;
                if (column < PlaytimeWeekday.ALL_DAYS) dayMax = Math.max(dayMax, value);
                else allMax = Math.max(allMax, value);
            }
        }
        for (int column = 0; column <= PlaytimeWeekday.ALL_DAYS; column++) {
            int left = columns.barLeft(column);
            long max = column == PlaytimeWeekday.ALL_DAYS ? allMax : dayMax;
            for (int hour = 0; hour < PlaytimeWeekday.HOURS; hour++) {
                int top = hour * height / PlaytimeWeekday.HOURS;
                int bottom = (hour + 1) * height / PlaytimeWeekday.HOURS;
                heat.fill(left, top, left + columns.bar(), bottom, shade(values[column][hour], max));
            }
        }
        heat.upload();
    }

    private static void outlineCell(OwoUIGraphics graphics, int scale, int left, int width, int top, int bottom) {
        int right = left + width;
        int line = Math.max(1, scale / 2);
        PlaytimePainter.inPixels(graphics, scale, () -> {
            graphics.fill(left - line, top - line, right + line, top, HOVER_OUTLINE);
            graphics.fill(left - line, bottom, right + line, bottom + line, HOVER_OUTLINE);
            graphics.fill(left - line, top, left, bottom, HOVER_OUTLINE);
            graphics.fill(right, top, right + line, bottom, HOVER_OUTLINE);
        });
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
            PlaytimePainter.axisLabel(graphics, label, left - 4 - PlaytimePainter.axisWidth(label), lineY - 3);
        }
    }

    private static void dayLabel(OwoUIGraphics graphics, String label, int pixelLeft, int pixelWidth, int scale, int y) {
        int center = (pixelLeft + pixelWidth / 2) / scale;
        PlaytimePainter.axisLabel(graphics, label, center - PlaytimePainter.axisWidth(label) / 2, y);
    }

    private String durationLabel(double value) {
        return PlaytimeDuration.axis(Math.round(value));
    }

    /**
     * Purple through magenta to orange as playtime grows. Empty hours stay dark.
     */
    private static int shade(long value, long max) {
        if (value <= 0 || max <= 0) return HEAT_EMPTY;
        double t = Math.pow(value / (double) max, 0.6) * (HEAT_STOPS.length - 1);
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
