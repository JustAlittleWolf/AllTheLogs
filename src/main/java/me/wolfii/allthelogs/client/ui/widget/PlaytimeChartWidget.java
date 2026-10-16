package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.playtime.BarLayout;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel.Hit;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel.Plot;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel.Result;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeStyle;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;

/**
 * Timeline of estimated playtime. Every bucket is a bar of the same width in screen pixels, with no gap
 * between bars. The scroll wheel zooms around the pointer and dragging pans. While zoomed in, a thin
 * indicator under the axis shows where the view sits in the whole history. Right-click reports the
 * bucket, and the colored section when the pointer is on one.
 */
public final class PlaytimeChartWidget extends BaseUIComponent {
    private static final int LABEL_GAP = 8;

    private final SeriesHighlight highlight;
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

    public PlaytimeChartWidget(SeriesHighlight highlight) {
        this.highlight = highlight;
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
            highlight.chart(-1);
            PlaytimePainter.centered(graphics, placeholder, x, y, width, height);
            return;
        }
        Plot plot = PlaytimeChartModel.plot(width, height);
        int plotX = x + plot.x();
        int plotY = y + plot.y();
        int plotBottom = plotY + plot.height();
        int scale = PlaytimePainter.scale();
        BarLayout layout = layout(plot, scale);
        Hit hit = isInBoundingBox(mouseX, mouseY) ? hitAt(layout, scale, mouseX - x, mouseY - y) : null;
        highlight.chart(hit == null ? -1 : hit.series());
        PlaytimePainter.yAxis(graphics, y, plotX, plotY, plot.width(), plot.height(), chart.axisMax(), this::yLabel);
        int active = highlight.active();
        PlaytimePainter.inPixels(graphics, scale, () -> {
            for (int column = layout.first(); column < layout.first() + layout.count(); column++) {
                int left = layout.barLeft(column);
                PlaytimePainter.stacked(graphics, left, left + layout.barWidth(), plotBottom * scale,
                    plot.height() * scale, chart.axisMax(), chart.columns().get(column).display(), chart.series(), active);
            }
        });
        drawXAxis(graphics, layout, scale, plotBottom + 3);
        drawViewIndicator(graphics, plotX, plot.width());
        if (hit != null) {
            String unknown = Component.translatable("allthelogs.playtime.server.unknown").getString();
            String total = Component.translatable("allthelogs.playtime.hover.total").getString();
            PlaytimePainter.hoverCard(graphics, chart.hover(hit.column(), unknown, total), mouseX, mouseY, x, y, width, height);
        }
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (chart == null || chart.isEmpty()) return false;
        int scale = PlaytimePainter.scale();
        BarLayout layout = layout(PlaytimeChartModel.plot(width, height), scale);
        onZoom.accept(chart.fractionAt(layout, (x + mouseX) * scale), Math.clamp(amount, -5d, 5d));
        return true;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (focusHandler() != null) {
            focusHandler().focus(this, UIComponent.FocusSource.MOUSE_CLICK);
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (chart != null && !chart.isEmpty()) {
                int scale = PlaytimePainter.scale();
                Hit hit = hitAt(layout(PlaytimeChartModel.plot(width, height), scale), scale, click.x(), click.y());
                if (hit != null) onContext.open(hit, x + click.x(), y + click.y());
            }
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

    private BarLayout layout(Plot plot, int scale) {
        return BarLayout.of(chart.columns().size(), (x + plot.x()) * scale, plot.width() * scale);
    }

    private Hit hitAt(BarLayout layout, int scale, double localX, double localY) {
        int column = layout.columnAt((x + localX) * scale);
        return chart.hit(column, localY, PlaytimeChartModel.plot(width, height));
    }

    /**
     * Labels under the bars. Labelled columns are a fixed multiple apart, so labels stay put while panning.
     */
    private void drawXAxis(OwoUIGraphics graphics, BarLayout layout, int scale, int labelY) {
        List<PlaytimeChartModel.Column> columns = chart.columns();
        if (layout.count() == 0) return;
        int widest = Math.max(labelWidth(columns.get(layout.first())),
            labelWidth(columns.get(layout.first() + layout.count() - 1)));
        int step = Math.max(1, (int) Math.ceil((widest + LABEL_GAP) * scale / (double) layout.barWidth()));
        int first = Math.ceilDiv(layout.first(), step) * step;
        for (int column = first; column < layout.first() + layout.count(); column += step) {
            PlaytimeChartModel.Column bucket = columns.get(column);
            String label = PlaytimeChartModel.spanLabel(bucket.start(), bucket.end(), chart.grain());
            int textWidth = PlaytimePainter.axisWidth(label);
            int center = (layout.barLeft(column) + layout.barWidth() / 2) / scale;
            int textX = Math.max(x, Math.min(center - textWidth / 2, x + width - textWidth));
            PlaytimePainter.axisLabel(graphics, label, textX, labelY);
        }
    }

    private int labelWidth(PlaytimeChartModel.Column bucket) {
        return PlaytimePainter.axisWidth(PlaytimeChartModel.spanLabel(bucket.start(), bucket.end(), chart.grain()));
    }

    private void drawViewIndicator(OwoUIGraphics graphics, int left, int span) {
        long domainStart = epoch(chart.domainStart());
        long domainEnd = epoch(chart.domainEnd());
        long viewStart = epoch(chart.viewStart());
        long viewEnd = epoch(chart.viewEnd());
        if (domainEnd <= domainStart || (viewStart <= domainStart && viewEnd >= domainEnd)) return;
        double length = domainEnd - domainStart;
        int thumbLeft = left + (int) Math.round((viewStart - domainStart) / length * span);
        int thumbRight = Math.max(thumbLeft + 2, left + (int) Math.round((viewEnd - domainStart) / length * span));
        int bottom = y + height;
        graphics.fill(left, bottom - 2, left + span, bottom, Colors.TRACK);
        graphics.fill(thumbLeft, bottom - 2, thumbRight, bottom, Colors.MUTED);
    }

    private String yLabel(double value) {
        if (chart.style() == PlaytimeStyle.PERCENT) return Math.round(value * 100) + "%";
        return PlaytimeDuration.format(Math.round(value));
    }

    private static long epoch(LocalDateTime time) {
        return time.toEpochSecond(ZoneOffset.UTC);
    }

    @FunctionalInterface
    public interface ContextHandler {
        void open(Hit hit, double screenX, double screenY);
    }
}
