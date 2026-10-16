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

/**
 * Weekday bars on the left and the hour-of-day heat map on the right. The bars run Monday to Sunday and
 * are all the same width in screen pixels. Hovering a bar or a heat map cell shows its playtime. Both
 * halves live in one component so the hover card can reach across them.
 */
public final class WeekdayChartWidget extends BaseUIComponent {
    private static final int SPLIT_GAP = 10;

    private final SeriesHighlight highlight;
    private PlaytimeWeekday.Result chart;
    private Component placeholder = Component.empty();

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

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(x, y, x + width, y + height, Colors.LIST_BACKGROUND);
        if (chart == null || chart.isEmpty()) {
            highlight.chart(-1);
            PlaytimePainter.centered(graphics, placeholder, x, y, width, height);
            return;
        }
        boolean inside = isInBoundingBox(mouseX, mouseY);
        int barsWidth = Math.max(140, width * 2 / 5);
        List<PlaytimeHover.Line> card = drawBars(graphics, barsWidth, mouseX, mouseY, inside);
        int heatX = x + barsWidth + SPLIT_GAP;
        List<PlaytimeHover.Line> heatCard = HeatmapPainter.draw(graphics, chart, highlight.active(), heatX, y,
            x + width - heatX, height, mouseX, mouseY, inside);
        if (card == null) card = heatCard;
        if (card != null) PlaytimePainter.hoverCard(graphics, card, mouseX, mouseY, x, y, width, height);
    }

    private List<PlaytimeHover.Line> drawBars(OwoUIGraphics graphics, int areaWidth, int mouseX, int mouseY,
                                              boolean inside) {
        PlaytimeChartModel.Plot plot = PlaytimeChartModel.plot(areaWidth, height);
        int plotX = x + plot.x();
        int plotY = y + plot.y();
        int plotBottom = plotY + plot.height();
        int scale = PlaytimePainter.scale();
        int slot = plot.width() * scale / 7;
        int gap = Math.max(scale, slot / 5);
        int bar = Math.max(1, slot - gap);
        int left = plotX * scale + (plot.width() * scale - slot * 7 + gap) / 2;

        int hoveredDay = -1;
        int hoveredSeries = -1;
        if (inside && mouseY >= plotY && mouseY < plotBottom && slot > 0) {
            int day = Math.floorDiv(mouseX * scale - left, slot);
            if (day >= 0 && day < 7 && mouseX * scale - left - day * slot < bar) {
                hoveredDay = day;
                hoveredSeries = PlaytimeChartModel.seriesAt(chart.bars().get(day).display(), chart.axisMax(),
                    mouseY - y, plot.y(), plot.height());
            }
        }
        highlight.chart(hoveredSeries);

        PlaytimePainter.yAxis(graphics, y, plotX, plotY, plot.width(), plot.height(), chart.axisMax(),
            value -> PlaytimeDuration.format(Math.round(value)));
        int active = highlight.active();
        PlaytimePainter.inPixels(graphics, scale, () -> {
            for (int day = 0; day < 7; day++) {
                int barLeft = left + day * slot;
                PlaytimePainter.stacked(graphics, barLeft, barLeft + bar, plotBottom * scale, plot.height() * scale,
                    chart.axisMax(), chart.bars().get(day).display(), chart.series(), active);
            }
        });
        for (int day = 0; day < 7; day++) {
            String label = PlaytimeWeekday.shortDayName(day);
            int center = (left + day * slot + bar / 2) / scale;
            PlaytimePainter.axisLabel(graphics, label, center - PlaytimePainter.axisWidth(label) / 2, plotBottom + 3);
        }
        if (hoveredDay < 0) return null;
        return chart.dayHover(hoveredDay, Component.translatable("allthelogs.playtime.hover.total").getString(),
            Component.translatable("allthelogs.playtime.server.unknown").getString());
    }
}
