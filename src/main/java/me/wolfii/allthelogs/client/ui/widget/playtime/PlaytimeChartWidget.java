package me.wolfii.allthelogs.client.ui.widget.playtime;

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
import me.wolfii.allthelogs.client.playtime.PlaytimeHover;
import me.wolfii.allthelogs.client.playtime.PlaytimeStyle;
import me.wolfii.allthelogs.client.search.ActiveFilters;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Timeline of estimated playtime. Bars touch and fill the plot in screen pixels. Zoomed out, every bar is
 * whole; zoomed in, bars sit where their time falls, so panning moves them smoothly. The bars are painted into
 * a texture only when the chart, the size, or the highlighted series changes. The scroll wheel zooms
 * around the pointer and dragging pans. While zoomed in, a thin indicator under the axis shows where the
 * view sits in the whole history. The bucket under the pointer is shaded, and right-click reports it with
 * the colored section when the pointer is on one.
 */
public final class PlaytimeChartWidget extends BaseUIComponent implements AutoCloseable {
    /** Room kept free between two labels under the bars, in GUI pixels. */
    private static final int LABEL_GAP = 28;

    private final SeriesHighlight highlight;
    private final PixelCanvas canvas = new PixelCanvas();
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
    private BooleanSupplier hoverBlocked = () -> false;
    private boolean panning;

    private Result laidOut;
    private int laidOutWidth;
    private int laidOutHeight;
    private int laidOutScale;
    private int axisWidth;
    private BarLayout layout;
    private int[] drawnAt = new int[0];
    private boolean painted;
    private int paintedHighlight;
    private int hoverColumn = -1;
    private int hoverFocus = -1;
    private Hit pinned;
    private List<PlaytimeHover.Line> hoverLines = List.of();

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

    /**
     * While this is true the pointer neither highlights nor shows a hover card, e.g. while a menu is open.
     */
    public void hoverBlocked(BooleanSupplier hoverBlocked) {
        this.hoverBlocked = hoverBlocked;
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
        layOut(scale);
        Plot plot = plot();
        int plotX = x + plot.x();
        int plotY = y + plot.y();
        int plotBottom = plotY + plot.height();

        boolean blocked = hoverBlocked.getAsBoolean();
        int column = blocked || !isInBoundingBox(mouseX, mouseY) ? -1 : columnAt(mouseX * scale + scale / 2, scale);
        Hit hit = column < 0 ? null : chart.hit(column, mouseY - y, plot);
        if (hit == null) column = -1;
        boolean showPinned = blocked && pinned != null;
        if (showPinned) column = pinned.column();
        highlight.chart(showPinned ? pinned.series() : hit == null ? -1 : hit.series());
        int active = highlight.active();
        if (!painted || paintedHighlight != active) paint(plot, scale, active);

        PlaytimePainter.yAxis(graphics, y, plotX, plotY, plot.width(), plot.height(), chart.axisMax(), this::yLabel);
        canvas.draw(graphics, scale, plotX * scale, plotY * scale);
        if (column >= 0) {
            int left = plotX * scale + layout.barLeft(column);
            int right = Math.max(left + 1, plotX * scale + layout.barRight(column));
            PlaytimePainter.inPixels(graphics, scale, () ->
                graphics.fill(left, plotY * scale, right, plotBottom * scale, PlaytimePainter.HOVER_BAND));
        }
        drawXAxis(graphics, plotX, scale, plotBottom + 3);
        drawViewIndicator(graphics, plotX, plot.width());
        if (column >= 0 && !showPinned) {
            PlaytimePainter.hoverCard(graphics, hoverLines(column, hit.series()), mouseX, mouseY, x, y, width, height);
        }
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (chart == null || chart.isEmpty() || layout == null) return false;
        int scale = PlaytimePainter.scale();
        double pixelX = (x + mouseX) * scale - (x + plot().x()) * scale;
        onZoom.accept(chart.fractionAt(layout, pixelX), Math.clamp(amount, -5d, 5d));
        return true;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (focusHandler() != null) {
            focusHandler().focus(this, UIComponent.FocusSource.MOUSE_CLICK);
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (chart != null && !chart.isEmpty() && layout != null) {
                int scale = PlaytimePainter.scale();
                int column = columnAt((int) Math.round((x + click.x()) * scale), scale);
                Hit hit = chart.hit(column, click.y(), plot());
                if (hit != null) {
                    onContext.open(hit, x + click.x(), y + click.y());
                    pinned = hit;
                }
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
        Plot plot = plot();
        if (plot.width() > 0) onPan.accept(-deltaX / plot.width());
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        panning = false;
        return super.onMouseUp(click);
    }

    /**
     * Releases the bar texture. Drawing again repaints it, so a screen that is shown again keeps working.
     */
    @Override
    public void close() {
        canvas.close();
        painted = false;
    }

    /**
     * Stops showing the bucket that was right-clicked as selected. Call when its context menu closes.
     */
    public void unpin() {
        pinned = null;
    }

    /**
     * Width of the plot in GUI pixels, or 0 before the first layout.
     */
    public int plotWidth() {
        return width <= 0 ? 0 : plot().width();
    }

    private Plot plot() {
        return PlaytimeChartModel.plot(width, height, axisWidth);
    }

    /**
     * Recomputes bar positions when the chart or the size changed. Bar positions are relative to the plot.
     */
    private void layOut(int scale) {
        if (chart == laidOut && width == laidOutWidth && height == laidOutHeight && scale == laidOutScale) return;
        laidOut = chart;
        laidOutWidth = width;
        laidOutHeight = height;
        laidOutScale = scale;
        axisWidth = Math.max(PlaytimeChartModel.PLOT_LEFT / 2, PlaytimePainter.yAxisWidth(chart.axisMax(), this::yLabel));
        List<PlaytimeChartModel.Column> columns = chart.columns();
        int pixels = plot().width() * scale;
        layout = zoomedIn() ? timedLayout(columns, pixels) : BarLayout.of(columns.size(), 0, pixels);
        double[] totals = new double[columns.size()];
        for (int index = 0; index < totals.length; index++) {
            for (double value : columns.get(index).display()) totals[index] += value;
        }
        drawnAt = layout.drawnAt(totals);
        painted = false;
        pinned = null;
        hoverColumn = -1;
    }

    /**
     * Screen pixels between the start of the history and the left edge of the view, so patterns scroll with
     * the bars instead of staying put on screen.
     */
    private int patternShift(int pixelWidth) {
        if (!zoomedIn()) return 0;
        double span = Math.max(1, epoch(chart.viewEnd()) - epoch(chart.viewStart()));
        return (int) Math.round((epoch(chart.viewStart()) - epoch(chart.domainStart())) / span * pixelWidth);
    }

    private boolean zoomedIn() {
        return epoch(chart.viewStart()) > epoch(chart.domainStart()) || epoch(chart.viewEnd()) < epoch(chart.domainEnd());
    }

    private BarLayout timedLayout(List<PlaytimeChartModel.Column> columns, int pixelWidth) {
        long[] edges = new long[columns.size() + 1];
        for (int index = 0; index < columns.size(); index++) edges[index] = epoch(columns.get(index).start());
        if (!columns.isEmpty()) edges[columns.size()] = epoch(columns.getLast().end());
        return BarLayout.timed(edges, epoch(chart.viewStart()), epoch(chart.viewEnd()), 0, pixelWidth);
    }

    private void paint(Plot plot, int scale, int active) {
        canvas.reset(plot.width() * scale, plot.height() * scale);
        int unit = PlaytimePainter.patternUnit(scale);
        int bottom = plot.height() * scale;
        int offset = layout.left();
        int shift = patternShift(plot.width() * scale);
        int start = 0;
        for (int pixel = 1; pixel <= drawnAt.length; pixel++) {
            if (pixel < drawnAt.length && drawnAt[pixel] == drawnAt[start]) continue;
            int column = drawnAt[start];
            if (column >= 0) {
                PlaytimePainter.stack(canvas, offset + start, offset + pixel, bottom, bottom, chart.axisMax(),
                    chart.columns().get(column).display(), chart.series(), active, unit, shift);
            }
            start = pixel;
        }
        canvas.upload();
        painted = true;
        paintedHighlight = active;
    }

    /**
     * Column drawn at the screen pixel {@code pixelX}, or -1 beside the bars.
     */
    private int columnAt(int pixelX, int scale) {
        int relative = pixelX - (x + plot().x()) * scale - layout.left();
        if (relative < 0 || relative >= drawnAt.length) return -1;
        return drawnAt[relative];
    }

    private List<PlaytimeHover.Line> hoverLines(int column, int focus) {
        if (column != hoverColumn || focus != hoverFocus) {
            hoverColumn = column;
            hoverFocus = focus;
            hoverLines = chart.hover(column, ActiveFilters.name(null),
                Component.translatable("allthelogs.playtime.hover.total").getString(), focus);
        }
        return hoverLines;
    }

    /**
     * Labels under the bars. Labelled buckets are a fixed multiple apart counted from a fixed point in time,
     * so labels move with their bars while panning instead of jumping between them. Every label is the full
     * date, including the year.
     */
    private void drawXAxis(OwoUIGraphics graphics, int plotX, int scale, int labelY) {
        List<PlaytimeChartModel.Column> columns = chart.columns();
        int count = layout.count();
        if (count == 0) return;
        int widest = Math.max(labelWidth(columns.getFirst()), labelWidth(columns.getLast()));
        double averageBar = (layout.right() - layout.left()) / (double) count;
        int step = Math.max(1, (int) Math.ceil((widest + LABEL_GAP) * scale / Math.max(averageBar, 0.001)));
        for (int column = 0; column < count; column++) {
            PlaytimeChartModel.Column bucket = columns.get(column);
            if (Math.floorMod(ordinal(bucket.start()), step) != 0) continue;
            if (layout.barRight(column) <= layout.barLeft(column)) continue;
            String label = PlaytimeChartModel.axisLabel(bucket.start(), chart.grain());
            int textWidth = PlaytimePainter.axisWidth(label);
            int center = plotX + (layout.barLeft(column) + layout.barRight(column)) / 2 / scale;
            int textX = Math.max(x, Math.min(center - textWidth / 2, x + width - textWidth));
            PlaytimePainter.axisLabel(graphics, label, textX, labelY);
        }
    }

    /**
     * Position of a bucket counted from a fixed date, in the chart's interval.
     */
    private long ordinal(LocalDateTime start) {
        return switch (chart.grain()) {
            case WEEKS -> Math.floorDiv(start.toLocalDate().toEpochDay() + 3, 7);
            case MONTHS -> start.getYear() * 12L + start.getMonthValue();
            case YEARS -> start.getYear();
            case DAYS, AUTO -> start.toLocalDate().toEpochDay();
        };
    }

    private int labelWidth(PlaytimeChartModel.Column bucket) {
        return PlaytimePainter.axisWidth(PlaytimeChartModel.axisLabel(bucket.start(), chart.grain()));
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
        return PlaytimeDuration.axis(Math.round(value));
    }

    private static long epoch(LocalDateTime time) {
        return time.toEpochSecond(ZoneOffset.UTC);
    }

    @FunctionalInterface
    public interface ContextHandler {
        void open(Hit hit, double screenX, double screenY);
    }
}
