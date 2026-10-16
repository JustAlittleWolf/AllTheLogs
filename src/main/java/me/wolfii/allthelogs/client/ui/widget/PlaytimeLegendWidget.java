package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeColors;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.ui.text.TextFit;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Vertical legend: the total on top, then every series with its playtime, most first. Hovering a row
 * highlights that series in the charts. Meant to sit in a vertical scroll container.
 */
public final class PlaytimeLegendWidget extends BaseUIComponent {
    private static final int ROW = 11;
    private static final int SWATCH = 7;
    private static final int HIGHLIGHT_ROW = 0x30FFFFFF;

    private final SeriesHighlight highlight;
    private Component total = Component.empty();
    private List<PlaytimeSeries> series = List.of();
    private List<PlaytimeChartModel.LegendEntry> entries = List.of();

    public PlaytimeLegendWidget(SeriesHighlight highlight) {
        this.highlight = highlight;
        this.sizing(Sizing.fill(), Sizing.content());
    }

    public void total(Component total) {
        this.total = total == null ? Component.empty() : total;
    }

    public void entries(List<PlaytimeSeries> series, List<PlaytimeChartModel.LegendEntry> entries) {
        boolean resized = entries.size() != this.entries.size();
        this.series = series;
        this.entries = entries;
        if (resized) notifyParentIfMounted();
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return ROW + 3 + entries.size() * ROW;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = PlaytimePainter.font();
        graphics.drawText(Component.literal(TextFit.fit(total.getString(), width - 2)), x + 1, y + 1, 1f, Colors.TEXT);
        graphics.fill(x, y + ROW + 1, x + width, y + ROW + 2, Colors.SEPARATOR);
        String unknown = Component.translatable("allthelogs.playtime.server.unknown").getString();
        boolean pointerInside = isInBoundingBox(mouseX, mouseY) && (parent() == null || parent().isInBoundingBox(mouseX, mouseY));
        int hovered = -1;
        int active = highlight.active();
        int rowY = y + ROW + 3;
        for (PlaytimeChartModel.LegendEntry entry : entries) {
            if (entry.series() < 0 || entry.series() >= series.size()) continue;
            PlaytimeSeries item = series.get(entry.series());
            if (pointerInside && mouseY >= rowY && mouseY < rowY + ROW) hovered = entry.series();
            if (entry.series() == active) graphics.fill(x, rowY, x + width, rowY + ROW, HIGHLIGHT_ROW);
            int swatchY = rowY + (ROW - SWATCH) / 2;
            graphics.fill(x + 1, swatchY, x + 1 + SWATCH, swatchY + SWATCH, item.color());
            if (item.hatched()) {
                graphics.drawLine(x + 1, swatchY + SWATCH, x + 1 + SWATCH, swatchY, 1.5,
                    Color.ofArgb(PlaytimeColors.stripe(item.color())));
            }
            String duration = PlaytimeDuration.format(entry.millis());
            int durationWidth = font.width(duration);
            int nameX = x + SWATCH + 4;
            String name = TextFit.fit(item.label(unknown), x + width - durationWidth - 4 - nameX);
            graphics.drawText(Component.literal(name), nameX, rowY + 2, 1f, Colors.TEXT);
            graphics.drawText(Component.literal(duration), x + width - durationWidth - 1, rowY + 2, 1f, Colors.MUTED);
            rowY += ROW;
        }
        highlight.legend(hovered);
    }
}
