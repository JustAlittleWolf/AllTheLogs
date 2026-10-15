package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeWeekday;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Seven stacked bars, Monday on the left through Sunday on the right. The most and least played days
 * are outlined.
 */
public final class WeekdayChartWidget extends BaseUIComponent {
    private static final String[] DAY_KEYS = {
        "allthelogs.playtime.day.mon",
        "allthelogs.playtime.day.tue",
        "allthelogs.playtime.day.wed",
        "allthelogs.playtime.day.thu",
        "allthelogs.playtime.day.fri",
        "allthelogs.playtime.day.sat",
        "allthelogs.playtime.day.sun"
    };
    private static final float AXIS_SCALE = 0.75f;
    private static final int MOST = 0xFFFFFFFF;
    private static final int LEAST = 0xFF8E8E8E;

    private PlaytimeWeekday.Result chart;
    private Component placeholder = Component.empty();

    public WeekdayChartWidget() {
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
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(placeholder);
            graphics.drawText(placeholder, x + Math.max(0, (width - textWidth) / 2),
                y + Math.max(0, (height - font.lineHeight) / 2), 1f, Colors.MUTED);
            return;
        }
        PlaytimeChartModel.Plot plot = PlaytimeChartModel.plot(width, height);
        int plotX = x + plot.x();
        int plotY = y + plot.y();
        int plotBottom = plotY + plot.height();
        graphics.fill(plotX, plotBottom, plotX + plot.width(), plotBottom + 1, Colors.TRACK_BORDER);
        drawYAxis(graphics, plotX, plotY, plot.height());
        int[] colors = new int[chart.series().size()];
        for (int index = 0; index < colors.length; index++) colors[index] = chart.series().get(index).color();
        double slot = plot.width() / 7.0;
        Font font = Minecraft.getInstance().font;
        for (int day = 0; day < 7; day++) {
            int left = plotX + (int) Math.round(day * slot) + 3;
            int right = plotX + (int) Math.round((day + 1) * slot) - 3;
            if (right <= left) right = left + 1;
            PlaytimeWeekday.Bar bar = chart.bars().get(day);
            PlaytimeBars.stacked(graphics, left, right, plotBottom, plot.height(), chart.axisMax(), bar.display(), colors);
            int stackTop = plotBottom - (int) Math.round(bar.totalMillis() / chart.axisMax() * plot.height());
            if (day == chart.mostDay() && bar.totalMillis() > 0) {
                PlaytimeBars.outline(graphics, left, stackTop, right, plotBottom, MOST);
            } else if (day == chart.leastDay() && chart.mostDay() != chart.leastDay()) {
                PlaytimeBars.outline(graphics, left, Math.min(stackTop, plotBottom - 1), right, plotBottom, LEAST);
            }
            String label = Component.translatable(DAY_KEYS[day]).getString();
            int textWidth = Math.round(font.width(label) * AXIS_SCALE);
            int center = (left + right) / 2;
            graphics.drawText(Component.literal(label), center - textWidth / 2, plotBottom + 3, AXIS_SCALE, Colors.TICK_LABEL);
        }
    }

    private void drawYAxis(OwoUIGraphics graphics, int plotX, int plotY, int plotHeight) {
        Font font = Minecraft.getInstance().font;
        for (int step = 0; step <= 3; step++) {
            double fraction = step / 3.0;
            int lineY = plotY + plotHeight - (int) Math.round(fraction * plotHeight);
            graphics.fill(plotX, lineY, plotX + PlaytimeChartModel.plot(width, height).width(), lineY + 1,
                step == 0 ? Colors.TRACK_BORDER : Colors.SEPARATOR);
            String label = PlaytimeDuration.format(Math.round(chart.axisMax() * fraction));
            int textWidth = Math.round(font.width(label) * AXIS_SCALE);
            graphics.drawText(Component.literal(label), plotX - 4 - textWidth, Math.max(y, lineY - font.lineHeight),
                AXIS_SCALE, Colors.TICK_LABEL);
        }
    }
}
