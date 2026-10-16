package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel.Hit;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel.Plot;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel.Result;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeStyle;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;

/**
 * Timeline of estimated playtime. The scroll wheel zooms around the pointer and dragging pans.
 * Right-click reports the bucket, and the colored section when the pointer is on one.
 */
public final class PlaytimeChartWidget extends BaseUIComponent {
    private static final int MAX_HOVER_LINES = 14;
    private static final float AXIS_SCALE = 0.75f;

    private Result chart;
    private Component placeholder = Component.empty();
    private BiConsumer<Double, Double> onZoom = (fraction, steps) -> {
    };
    private DoubleConsumer onPan = fraction -> {
    };
    private Runnable onReset = () -> {
    };
    private ContextHandler onContext = (hit, screenX, screenY) -> {
    };
    private Runnable onDismiss = () -> {
    };
    private boolean panning;

    public PlaytimeChartWidget() {
        this.sizing(Sizing.fill(), Sizing.fill());
    }

    public void chart(Result chart) {
        this.chart = chart;
    }

    public void placeholder(Component placeholder) {
        this.placeholder = placeholder == null ? Component.empty() : placeholder;
    }

    public void onZoom(BiConsumer<Double, Double> onZoom) {
        this.onZoom = onZoom;
    }

    public void onPan(DoubleConsumer onPan) {
        this.onPan = onPan;
    }

    public void onReset(Runnable onReset) {
        this.onReset = onReset;
    }

    public void onContext(ContextHandler onContext) {
        this.onContext = onContext;
    }

    public void onDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(x, y, x + width, y + height, Colors.LIST_BACKGROUND);
        if (chart == null || chart.isEmpty()) {
            drawCentered(graphics, placeholder);
            return;
        }
        Plot plot = PlaytimeChartModel.plot(width, height);
        int plotX = x + plot.x();
        int plotY = y + plot.y();
        int plotBottom = plotY + plot.height();
        int plotRight = plotX + plot.width();
        graphics.fill(plotX, plotBottom, plotRight, plotBottom + 1, Colors.TRACK_BORDER);
        drawYAxis(graphics, plotX, plotY, plot.height());
        int[] colors = colors();
        for (PlaytimeChartModel.Column column : chart.columns()) {
            int left = xAt(plot, column.start());
            int right = xAt(plot, column.end());
            if (right - left > 3) {
                left++;
                right--;
            }
            if (right <= left) right = left + 1;
            left = Math.max(plotX, left);
            right = Math.min(plotRight, right);
            PlaytimeBars.stacked(graphics, left, right, plotBottom, plot.height(), chart.axisMax(),
                column.display(), colors);
        }
        drawXAxis(graphics, plotY + plot.height() + 3);
        int localX = mouseX - x;
        int localY = mouseY - y;
        if (localX >= 0 && localX < width && localY >= 0 && localY < height) {
            Hit hit = chart.hit(localX, localY, plot);
            if (hit != null) drawHover(graphics, mouseX, mouseY, hit.column());
        }
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (chart == null || chart.isEmpty()) return false;
        Plot plot = PlaytimeChartModel.plot(width, height);
        double fraction = plot.width() <= 0 ? 0.5 : (mouseX - plot.x()) / plot.width();
        onZoom.accept(fraction, Math.clamp(amount, -5d, 5d));
        return true;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (focusHandler() != null) {
            focusHandler().focus(this, UIComponent.FocusSource.MOUSE_CLICK);
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            Hit hit = hitAt(click.x(), click.y());
            if (hit != null) onContext.open(hit, x + click.x(), y + click.y());
            return true;
        }
        onDismiss.run();
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubled) {
            panning = false;
            onReset.run();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            panning = true;
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!panning || click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || chart == null || chart.isEmpty()) {
            return super.onMouseDrag(click, deltaX, deltaY);
        }
        Plot plot = PlaytimeChartModel.plot(width, height);
        if (plot.width() > 0) onPan.accept(-deltaX / plot.width());
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        panning = false;
        return super.onMouseUp(click);
    }

    private Hit hitAt(double localX, double localY) {
        if (chart == null || chart.isEmpty()) return null;
        return chart.hit(localX, localY, PlaytimeChartModel.plot(width, height));
    }

    private void drawYAxis(OwoUIGraphics graphics, int plotX, int plotY, int plotHeight) {
        Font font = font();
        for (int step = 0; step <= 3; step++) {
            double fraction = step / 3.0;
            int lineY = plotY + plotHeight - (int) Math.round(fraction * plotHeight);
            graphics.fill(plotX, lineY, plotX + PlaytimeChartModel.plot(width, height).width(), lineY + 1,
                step == 0 ? Colors.TRACK_BORDER : Colors.SEPARATOR);
            String label = yLabel(chart.axisMax() * fraction);
            int textWidth = Math.round(font.width(label) * AXIS_SCALE);
            int textY = Math.max(y, lineY - font.lineHeight);
            graphics.drawText(Component.literal(label), plotX - 4 - textWidth, textY, AXIS_SCALE, Colors.TICK_LABEL);
        }
    }

    private void drawXAxis(OwoUIGraphics graphics, int labelY) {
        List<PlaytimeChartModel.Column> columns = chart.columns();
        if (columns.isEmpty()) return;
        int labels = Math.min(6, columns.size());
        int step = Math.max(1, (int) Math.ceil(columns.size() / (double) labels));
        Plot plot = PlaytimeChartModel.plot(width, height);
        Font font = font();
        for (int index = 0; index < columns.size(); index += step) {
            PlaytimeChartModel.Column column = columns.get(index);
            String label = PlaytimeChartModel.spanLabel(column.start(), column.end(), chart.grain());
            int textWidth = Math.round(font.width(label) * AXIS_SCALE);
            int center = (xAt(plot, column.start()) + xAt(plot, column.end())) / 2;
            int textX = Math.max(x, Math.min(center - textWidth / 2, x + width - textWidth));
            graphics.drawText(Component.literal(label), textX, y + labelY, AXIS_SCALE, Colors.TICK_LABEL);
        }
    }

    private String yLabel(double value) {
        if (chart.style() == PlaytimeStyle.PERCENT) return Math.round(value * 100) + "%";
        return PlaytimeDuration.format(Math.round(value));
    }

    private void drawHover(OwoUIGraphics graphics, int mouseX, int mouseY, int column) {
        String unknown = Component.translatable("item.canUse.unknown").getString();
        String total = Component.translatable("allthelogs.playtime.hover.total").getString();
        List<PlaytimeChartModel.HoverLine> lines = chart.hover(column, unknown, total);
        if (lines.isEmpty()) return;
        int hidden = Math.max(0, lines.size() - MAX_HOVER_LINES);
        int shown = lines.size() - hidden;
        Font font = font();
        int lineHeight = font.lineHeight;
        int textWidth = 0;
        for (int index = 0; index < shown; index++) {
            textWidth = Math.max(textWidth, font.width(lines.get(index).text()));
        }
        String more = null;
        if (hidden > 0) {
            more = Component.translatable("allthelogs.playtime.hover.more", hidden).getString();
            textWidth = Math.max(textWidth, font.width(more));
            shown++;
        }
        int pad = 4;
        int boxWidth = Math.min(width - 8, textWidth + pad * 2);
        int boxHeight = pad * 2 + shown * lineHeight;
        int boxX = Math.min(mouseX + 8, x + width - boxWidth - 2);
        boxX = Math.max(x + 2, boxX);
        int boxY = mouseY + 12;
        if (boxY + boxHeight > y + height) boxY = Math.max(y + 2, mouseY - boxHeight - 4);
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, Colors.HOVER_CHIP);
        int textY = boxY + pad;
        for (int index = 0; index < lines.size() - hidden; index++) {
            PlaytimeChartModel.HoverLine line = lines.get(index);
            graphics.drawText(Component.literal(line.text()), boxX + pad, textY, 1f, line.color());
            textY += lineHeight;
        }
        if (more != null) {
            graphics.drawText(Component.literal(more), boxX + pad, textY, 1f, Colors.MUTED);
        }
    }

    private void drawCentered(OwoUIGraphics graphics, Component text) {
        Font font = font();
        int textWidth = font.width(text);
        graphics.drawText(text, x + Math.max(0, (width - textWidth) / 2), y + Math.max(0, (height - font.lineHeight) / 2),
            1f, Colors.MUTED);
    }

    private int xAt(Plot plot, LocalDateTime time) {
        long start = chart.viewStart().toEpochSecond(ZoneOffset.UTC);
        long end = chart.viewEnd().toEpochSecond(ZoneOffset.UTC);
        double fraction = end <= start ? 0 : (time.toEpochSecond(ZoneOffset.UTC) - start) / (double) (end - start);
        fraction = Math.clamp(fraction, 0d, 1d);
        return x + plot.x() + (int) Math.round(fraction * plot.width());
    }

    private int[] colors() {
        int[] colors = new int[chart.series().size()];
        for (int index = 0; index < colors.length; index++) colors[index] = chart.series().get(index).color();
        return colors;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    @FunctionalInterface
    public interface ContextHandler {
        void open(Hit hit, double screenX, double screenY);
    }
}
