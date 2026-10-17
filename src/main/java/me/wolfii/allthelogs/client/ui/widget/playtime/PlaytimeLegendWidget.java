package me.wolfii.allthelogs.client.ui.widget.playtime;

import io.wispforest.owo.ui.base.BaseUIComponent;
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
import java.util.function.BooleanSupplier;

/**
 * Vertical legend: every series with its playtime, most first, and the same pattern as in the charts.
 * Hovering a row highlights that series in the charts, and shows the full name when it is cut short. Meant to sit in a vertical scroll container; the
 * rightmost pixels stay free for its scrollbar.
 */
public final class PlaytimeLegendWidget extends BaseUIComponent implements AutoCloseable {
    private static final int ROW = 11;
    private static final int SWATCH = 7;
    private static final int SCROLLBAR_ROOM = 5;
    /** Gap between a duration and the right edge of the legend; the total above the legend uses it too. */
    public static final int VALUE_INSET = SCROLLBAR_ROOM + 1;
    private static final int HIGHLIGHT_ROW = 0x30FFFFFF;

    private final SeriesHighlight highlight;
    private final PixelCanvas swatches = new PixelCanvas();
    private BooleanSupplier hoverBlocked = () -> false;
    private List<PlaytimeSeries> series = List.of();
    private List<PlaytimeChartModel.LegendEntry> entries = List.of();
    private boolean swatchesPainted;
    private int swatchScale;

    public PlaytimeLegendWidget(SeriesHighlight highlight) {
        this.highlight = highlight;
        this.sizing(Sizing.fill(), Sizing.content());
    }

    public void entries(List<PlaytimeSeries> series, List<PlaytimeChartModel.LegendEntry> entries) {
        boolean resized = entries.size() != this.entries.size();
        this.series = series;
        this.entries = entries;
        swatchesPainted = false;
        if (resized) notifyParentIfMounted();
    }

    public void hoverBlocked(BooleanSupplier hoverBlocked) {
        this.hoverBlocked = hoverBlocked;
    }

    /**
     * Releases the swatch texture. Drawing again repaints it.
     */
    @Override
    public void close() {
        swatches.close();
        swatchesPainted = false;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return entries.size() * ROW;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = PlaytimePainter.font();
        int scale = PlaytimePainter.scale();
        if (!swatchesPainted || swatchScale != scale) paintSwatches(scale);
        String unknown = Component.translatable("allthelogs.playtime.server.unknown").getString();
        boolean pointerInside = !hoverBlocked.getAsBoolean() && isInBoundingBox(mouseX, mouseY)
            && (parent() == null || parent().isInBoundingBox(mouseX, mouseY));
        int right = x + width - SCROLLBAR_ROOM;
        int hovered = -1;
        String cutName = null;
        int active = highlight.active();
        int rowY = y;
        for (PlaytimeChartModel.LegendEntry entry : entries) {
            if (entry.series() >= 0 && entry.series() < series.size()) {
                PlaytimeSeries item = series.get(entry.series());
                boolean rowHovered = pointerInside && mouseY >= rowY && mouseY < rowY + ROW;
                if (rowHovered) hovered = entry.series();
                if (entry.series() == active) graphics.fill(x, rowY, right, rowY + ROW, HIGHLIGHT_ROW);
                String duration = PlaytimeDuration.format(entry.millis());
                int durationWidth = font.width(duration);
                int nameX = x + SWATCH + 4;
                String fullName = item.label(unknown);
                String name = TextFit.fit(fullName, right - durationWidth - 4 - nameX);
                if (rowHovered && !name.equals(fullName)) cutName = fullName;
                graphics.drawText(Component.literal(name), nameX, rowY + 2, 1f, Colors.TEXT);
                graphics.drawText(Component.literal(duration), x + width - VALUE_INSET - durationWidth, rowY + 2, 1f,
                    Colors.MUTED);
            }
            rowY += ROW;
        }
        swatches.draw(graphics, scale, (x + 1) * scale, y * scale);
        highlight.legend(hovered);
        if (cutName != null) graphics.setTooltipForNextFrame(font, Component.literal(cutName), mouseX, mouseY);
    }

    /**
     * One swatch per row, painted once per list so the patterns do not cost a rectangle per pixel each frame.
     */
    private void paintSwatches(int scale) {
        swatches.reset(SWATCH * scale, Math.max(1, entries.size() * ROW * scale));
        int unit = PlaytimePainter.patternUnit(scale);
        int offset = (ROW - SWATCH) / 2 * scale;
        for (int row = 0; row < entries.size(); row++) {
            int index = entries.get(row).series();
            if (index < 0 || index >= series.size()) continue;
            PlaytimeSeries item = series.get(index);
            int top = row * ROW * scale + offset;
            swatches.fill(0, top, SWATCH * scale, top + SWATCH * scale, item.color(),
                PlaytimeColors.swatchStripe(item.color()), item.pattern(), unit, 0);
        }
        swatches.upload();
        swatchesPainted = true;
        swatchScale = scale;
    }
}
