package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.view.DisplayRow;
import me.wolfii.allthelogs.view.ResultWindow;
import me.wolfii.allthelogs.view.TimelineLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Virtualised log list plus a timeline scrollbar. The scrollbar maps the full match range, not only the
 * currently loaded page; paging keeps the anchored row on screen so loading more does not jump.
 */
public final class TimelineLogList extends BaseUIComponent {
    public static final int ROW_HEIGHT = 12;
    public static final int TIMELINE_WIDTH = 64;
    private static final int MARKER = 0xFFE8F5C0;
    private static final int TRACK = 0xFF1A1A1A;
    private static final int THUMB = 0x88FFFFFF;
    private static final int TICK = 0xFF888888;

    private final ResultWindow window = new ResultWindow();
    private List<LocalDateTime> markers = List.of();
    private double scrollY;
    private boolean loading;
    private boolean draggingTimeline;
    private Consumer<Edge> onApproachEdge = edge -> {};
    private Consumer<LocalDateTime> onJump = time -> {};

    public TimelineLogList() {
        this.sizing(Sizing.fill(), Sizing.fill());
        this.cursorStyle(CursorStyle.POINTER);
    }

    public ResultWindow window() {
        return window;
    }

    public void onApproachEdge(Consumer<Edge> onApproachEdge) {
        this.onApproachEdge = onApproachEdge;
    }

    public void onJump(Consumer<LocalDateTime> onJump) {
        this.onJump = onJump;
    }

    public void setMarkers(List<LocalDateTime> markers) {
        this.markers = List.copyOf(markers);
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean loading() {
        return loading;
    }

    public double scrollY() {
        return scrollY;
    }

    public void setScrollY(double scrollY) {
        this.scrollY = clampScroll(scrollY);
    }

    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        window.reset(rows, hasBefore, hasAfter);
        this.scrollY = 0;
    }

    public void applyPage(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, DisplayRow.RowKey anchor) {
        this.scrollY = window.replaceKeepingAnchor(rows, hasBefore, hasAfter, anchor, scrollY, ROW_HEIGHT);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int listWidth = Math.max(0, width - TIMELINE_WIDTH);
        graphics.fill(x, y, x + listWidth, y + height, 0x66000000);
        drawRows(graphics, listWidth);
        drawTimeline(graphics, x + listWidth, mouseX, mouseY);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (mouseX >= x + width - TIMELINE_WIDTH) {
            return false;
        }
        setScrollY(scrollY - amount * ROW_HEIGHT * 3);
        maybeRequestMore();
        return true;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.x() >= x + width - TIMELINE_WIDTH) {
            draggingTimeline = true;
            jumpToY(click.y());
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingTimeline) {
            jumpToY(click.y() + deltaY);
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        draggingTimeline = false;
        return super.onMouseUp(click);
    }

    private void drawRows(OwoUIGraphics graphics, int listWidth) {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) {
            graphics.drawText(Component.translatable("allthelogs.status.empty"), x + 8, y + 8, 1, 0xA0A0A0);
            return;
        }
        Font font = Minecraft.getInstance().font;
        int first = (int) Math.floor(scrollY / ROW_HEIGHT);
        int last = Math.min(rows.size() - 1, first + height / ROW_HEIGHT + 1);
        first = Math.max(0, first);
        int timestampWidth = font.width("0000-00-00 00:00:00  ");
        for (int i = first; i <= last; i++) {
            DisplayRow row = rows.get(i);
            int rowY = y + (int) Math.round(i * (double) ROW_HEIGHT - scrollY);
            graphics.drawText(MessageComponents.timestamp(row), x + 4, rowY + 1, 1, 0xA0A0A0);
            graphics.drawText(MessageComponents.message(row), x + 4 + timestampWidth, rowY + 1, 1, 0xFFFFFF);
        }
        if (loading) {
            graphics.drawText(Component.translatable("allthelogs.status.loading"), x + 8, y + height - 14, 1, 0xC8C8C8);
        }
    }

    private void drawTimeline(OwoUIGraphics graphics, int trackX, int mouseX, int mouseY) {
        graphics.fill(trackX, y, trackX + TIMELINE_WIDTH, y + height, TRACK);
        if (markers.size() < 2 && window.rows().isEmpty()) {
            return;
        }
        LocalDateTime first = markers.isEmpty() ? window.firstMatchTime() : markers.getFirst();
        LocalDateTime last = markers.isEmpty() ? window.lastMatchTime() : markers.getLast();
        if (first == null || last == null) return;

        List<LocalDateTime> drawn = TimelineLayout.downsample(markers, height, 3);
        for (LocalDateTime marker : drawn) {
            int markerY = TimelineLayout.y(marker, first, last, y, height);
            graphics.fill(trackX + 18, markerY, trackX + TIMELINE_WIDTH - 10, markerY + 1, MARKER);
        }
        for (TimelineLayout.DateTick tick : TimelineLayout.ticks(first, last)) {
            int tickY = TimelineLayout.y(tick.at(), first, last, y, height);
            graphics.drawText(Component.literal(tick.label()), trackX + 2, tickY - 4, 0.7f, TICK);
        }

        LocalDateTime viewStart = window.firstMatchTime();
        LocalDateTime viewEnd = window.lastMatchTime();
        if (viewStart != null && viewEnd != null) {
            int thumbTop = TimelineLayout.y(viewStart, first, last, y, height);
            int thumbBottom = Math.max(thumbTop + 4, TimelineLayout.y(viewEnd, first, last, y, height));
            graphics.fill(trackX + 16, thumbTop, trackX + TIMELINE_WIDTH - 8, thumbBottom, THUMB);
        }

        if (mouseX >= trackX && mouseX < trackX + TIMELINE_WIDTH && mouseY >= y && mouseY < y + height) {
            this.cursorStyle(CursorStyle.MOVE);
        } else {
            this.cursorStyle(CursorStyle.POINTER);
        }
    }

    private void jumpToY(double mouseY) {
        if (markers.size() < 2) return;
        LocalDateTime first = markers.getFirst();
        LocalDateTime last = markers.getLast();
        double progress = (mouseY - y) / Math.max(1, height - 1);
        progress = Math.clamp(progress, 0, 1);
        long millis = Math.round(Duration.between(first, last).toMillis() * progress);
        onJump.accept(first.plus(Duration.ofMillis(millis)));
    }

    private void maybeRequestMore() {
        if (loading || window.rows().isEmpty()) return;
        int firstVisible = (int) Math.floor(scrollY / ROW_HEIGHT);
        int lastVisible = (int) Math.floor((scrollY + height) / ROW_HEIGHT);
        if (window.hasBefore() && firstVisible <= 2) {
            onApproachEdge.accept(Edge.BEFORE);
        } else if (window.hasAfter() && lastVisible >= window.rows().size() - 3) {
            onApproachEdge.accept(Edge.AFTER);
        }
    }

    private double clampScroll(double value) {
        double max = Math.max(0, window.contentHeight(ROW_HEIGHT) - height);
        if (value < 0) return 0;
        if (value > max) return max;
        return value;
    }

    public enum Edge {
        BEFORE, AFTER
    }
}
