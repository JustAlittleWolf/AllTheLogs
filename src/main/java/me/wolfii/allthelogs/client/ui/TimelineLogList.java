package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.ResultWindow;
import me.wolfii.allthelogs.client.view.TimelineLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Virtualised log list plus an Immich-style timeline scrubber on the right. Newest is at the top.
 * The scrubber maps the full match range; paging keeps the anchored row on screen so loading more
 * does not jump.
 */
public final class TimelineLogList extends BaseUIComponent {
    public static final int ROW_HEIGHT = 12;
    public static final int TIMELINE_WIDTH = 52;
    private static final int TRACK_WIDTH = 10;
    private static final int THUMB_HEIGHT = 18;
    private static final int BANNER_MS = 2200;
    private static final DateTimeFormatter HOVER_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");

    private static final int LIST_BG = 0x66000000;
    private static final int TRACK = 0xFF2B2B2B;
    private static final int TRACK_BORDER = 0xFF3A3A3A;
    private static final int DOT = 0xFF9BE08A;
    private static final int THUMB = 0xD0FFFFFF;
    private static final int TICK = 0xFF9A9A9A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int BANNER_BG = 0xE0181818;
    private static final int BANNER_BORDER = 0xFF3C3C3C;
    private static final int HOVER_BG = 0xF01C1C1C;

    private final ResultWindow window = new ResultWindow();
    private List<LocalDateTime> markers = List.of();
    private double scrollY;
    private boolean loading;
    private boolean draggingTimeline;
    private int matchCount;
    private boolean showMatchBanner;
    private long bannerUntilMs;
    private Component overlayMessage = Component.empty();
    private Consumer<Edge> onApproachEdge = edge -> {
    };
    private Consumer<LocalDateTime> onJump = time -> {
    };

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

    /**
     * Shows {@code N matches found} over the list for a moment after a search. Stays while the
     * pointer is over the list, then fades out.
     */
    public void showMatchCount(int matches) {
        this.matchCount = matches;
        this.showMatchBanner = true;
        this.bannerUntilMs = System.currentTimeMillis() + BANNER_MS;
        this.overlayMessage = Component.empty();
    }

    public void showOverlay(Component message) {
        this.overlayMessage = message == null ? Component.empty() : message;
        this.showMatchBanner = false;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int listWidth = Math.max(0, width - TIMELINE_WIDTH);
        graphics.fill(x, y, x + listWidth, y + height, LIST_BG);
        drawRows(graphics, listWidth);
        drawBanner(graphics, listWidth, mouseX, mouseY);
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
        graphics.enableScissor(x, y, x + listWidth, y + height);
        try {
            if (rows.isEmpty()) {
                graphics.drawText(Component.translatable("allthelogs.status.empty"), x + 8, y + 8, 1, MUTED);
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
                graphics.drawText(MessageComponents.timestamp(row), x + 4, rowY + 1, 1, MUTED);
                graphics.drawText(MessageComponents.message(row), x + 4 + timestampWidth, rowY + 1, 1, TEXT);
            }
            if (loading) {
                graphics.drawText(Component.translatable("allthelogs.status.loading"), x + 8, y + height - 14, 1, TEXT);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawBanner(OwoUIGraphics graphics, int listWidth, int mouseX, int mouseY) {
        boolean overList = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + height;
        boolean persistent = !overlayMessage.getString().isEmpty();
        boolean timed = showMatchBanner && (overList || System.currentTimeMillis() < bannerUntilMs);
        if (!persistent && !timed) {
            showMatchBanner = false;
            return;
        }
        Component text = persistent
            ? overlayMessage
            : Component.translatable("allthelogs.status.matches", Integer.toString(matchCount));
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int boxWidth = Math.min(listWidth - 16, textWidth + 16);
        int boxHeight = 16;
        int boxX = x + 8;
        int boxY = y + 8;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BANNER_BG);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, BANNER_BORDER);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, BANNER_BORDER);
        graphics.drawText(text, boxX + 8, boxY + 4, 1, TEXT);
    }

    private void drawTimeline(OwoUIGraphics graphics, int columnX, int mouseX, int mouseY) {
        int trackRight = x + width - 2;
        int trackLeft = trackRight - TRACK_WIDTH;
        int trackX = trackLeft;
        graphics.fill(trackX, y, trackX + TRACK_WIDTH, y + height, TRACK);
        graphics.fill(trackX, y, trackX + 1, y + height, TRACK_BORDER);

        LocalDateTime oldest = TimelineLayout.oldest(markers);
        LocalDateTime newest = TimelineLayout.newest(markers);
        if (oldest == null || newest == null) {
            oldest = window.firstMatchTime();
            newest = window.lastMatchTime();
            if (oldest != null && newest != null && oldest.isAfter(newest)) {
                LocalDateTime swap = oldest;
                oldest = newest;
                newest = swap;
            }
        }
        if (oldest == null || newest == null) {
            return;
        }

        List<LocalDateTime> drawn = TimelineLayout.downsample(markers, height, 8);
        for (LocalDateTime marker : drawn) {
            int markerY = TimelineLayout.yFromNewest(marker, oldest, newest, y, height);
            graphics.fill(trackX + 3, markerY, trackX + TRACK_WIDTH - 3, markerY + 2, DOT);
        }
        for (TimelineLayout.DateTick tick : TimelineLayout.ticks(oldest, newest)) {
            int tickY = TimelineLayout.yFromNewest(tick.at(), oldest, newest, y, height);
            graphics.drawText(Component.literal(tick.label()), columnX + 2, tickY - 4, 0.7f, TICK);
        }

        LocalDateTime viewTime = visibleTime();
        if (viewTime != null) {
            int thumbCenter = TimelineLayout.yFromNewest(viewTime, oldest, newest, y, height);
            int thumbTop = Math.clamp(thumbCenter - THUMB_HEIGHT / 2, y, y + height - THUMB_HEIGHT);
            graphics.fill(trackX + 1, thumbTop, trackX + TRACK_WIDTH - 1, thumbTop + THUMB_HEIGHT, THUMB);
        }

        boolean overScrubber = mouseX >= columnX && mouseX < x + width && mouseY >= y && mouseY < y + height;
        this.cursorStyle(overScrubber || draggingTimeline ? CursorStyle.MOVE : CursorStyle.POINTER);
        if (overScrubber || draggingTimeline) {
            LocalDateTime hoverTime = timeAtY(mouseY);
            if (hoverTime != null) {
                String label = hoverTime.format(HOVER_DATE);
                Font font = Minecraft.getInstance().font;
                int labelWidth = (int) (font.width(label) * 0.85f) + 10;
                int labelHeight = 14;
                int labelY = Math.clamp(mouseY - labelHeight / 2, y, y + height - labelHeight);
                int labelX = trackX - 6 - labelWidth;
                graphics.fill(labelX, labelY, labelX + labelWidth, labelY + labelHeight, HOVER_BG);
                graphics.fill(labelX, labelY, labelX + labelWidth, labelY + 1, BANNER_BORDER);
                graphics.drawText(Component.literal(label), labelX + 5, labelY + 3, 0.85f, TEXT);
            }
        }
    }

    private LocalDateTime visibleTime() {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) return null;
        int index = (int) Math.floor(scrollY / ROW_HEIGHT);
        index = Math.clamp(index, 0, rows.size() - 1);
        return rows.get(index).entry().timestamp();
    }

    private LocalDateTime timeAtY(double mouseY) {
        LocalDateTime oldest = TimelineLayout.oldest(markers);
        LocalDateTime newest = TimelineLayout.newest(markers);
        if (oldest == null || newest == null) {
            oldest = window.firstMatchTime();
            newest = window.lastMatchTime();
        }
        if (oldest == null || newest == null) return null;
        double progress = (mouseY - y) / Math.max(1, height - 1);
        return TimelineLayout.timeFromNewest(progress, oldest, newest);
    }

    private void jumpToY(double mouseY) {
        LocalDateTime time = timeAtY(mouseY);
        if (time == null) return;
        onJump.accept(time);
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
