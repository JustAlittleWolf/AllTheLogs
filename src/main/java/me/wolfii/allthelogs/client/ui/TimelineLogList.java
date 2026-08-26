package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.MessageListLayout;
import me.wolfii.allthelogs.client.view.MessageSelection;
import me.wolfii.allthelogs.client.view.MessageWrap;
import me.wolfii.allthelogs.client.view.ResultWindow;
import me.wolfii.allthelogs.client.view.TimelineLayout;
import me.wolfii.allthelogs.data.MatchBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * Virtualised log list plus a timeline scrubber on the right. Newest is at the top. The scrubber maps the
 * matched-log range only; date ticks stay hidden until the pointer is near the track.
 */
public final class TimelineLogList extends BaseUIComponent {
    public static final int ROW_HEIGHT = MessageListLayout.ROW_HEIGHT;
    public static final int TIMELINE_WIDTH = 52;
    private static final int TRACK_WIDTH = 10;
    private static final int THUMB_HEIGHT = 18;
    private static final int BANNER_MS = 2200;
    private static final int HOVER_SLOP = 10;

    private static final int LIST_BG = 0x80000000;
    private static final float DATE_SCALE = 1.3f;
    private static final int TRACK = 0xFF2B2B2B;
    private static final int TRACK_BORDER = 0xFF3A3A3A;
    private static final int THUMB = 0xD0FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int BANNER_BG = 0xE0181818;
    private static final int BANNER_BORDER = 0xFF3C3C3C;
    private static final int HOVER_BG = 0xF01C1C1C;
    private static final int DATE_BG = 0xE0141414;
    private static final int SELECTION = 0x663A6EA5;

    private final ResultWindow window = new ResultWindow();
    private final MessageSelection selection = new MessageSelection();
    private MessageListLayout layout = MessageListLayout.of(List.of(), 0);
    private int contextLines;
    private LocalDateTime rangeOldest;
    private LocalDateTime rangeNewest;
    private int uniqueMatchDates;
    private double scrollY;
    private boolean loading;
    private boolean draggingTimeline;
    private boolean draggingSelection;
    private double scrubY = Double.NaN;
    private int laidOutWidth = -1;
    private int matchCount;
    private boolean showMatchBanner;
    private long bannerUntilMs;
    private Component overlayMessage = Component.empty();
    private Consumer<Edge> onApproachEdge = edge -> {
    };
    private Consumer<LocalDateTime> onJump = time -> {
    };
    private Consumer<DisplayRow> onExpand = row -> {
    };

    public TimelineLogList() {
        this.sizing(Sizing.fill(), Sizing.fill());
        this.cursorStyle(CursorStyle.POINTER);
    }

    public ResultWindow window() {
        return window;
    }

    public MessageListLayout layout() {
        return layout;
    }

    public void onApproachEdge(Consumer<Edge> onApproachEdge) {
        this.onApproachEdge = onApproachEdge;
    }

    public void onJump(Consumer<LocalDateTime> onJump) {
        this.onJump = onJump;
    }

    public void onExpand(Consumer<DisplayRow> onExpand) {
        this.onExpand = onExpand;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
        rebuildLayout();
    }

    public void setMatchBounds(MatchBounds bounds) {
        if (bounds == null) {
            this.rangeOldest = null;
            this.rangeNewest = null;
            this.uniqueMatchDates = 0;
            return;
        }
        this.rangeOldest = bounds.oldest();
        this.rangeNewest = bounds.newest();
        this.uniqueMatchDates = bounds.uniqueDates();
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

    public int firstVisibleIndex() {
        return Math.max(0, layout.rowAtY(scrollY));
    }

    public int lastVisibleIndex() {
        if (layout.size() == 0) return 0;
        return Math.max(firstVisibleIndex(), layout.rowAtY(scrollY + Math.max(0, height)));
    }

    public DisplayRow.RowKey visibleAnchor() {
        return window.keyAtY(scrollY, layout::rowY, layout.size());
    }

    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        this.scrollY = 0;
        selection.clear();
    }

    public void scrollToTime(LocalDateTime time) {
        int index = window.nearestIndex(time);
        if (index < 0) return;
        setScrollY(layout.rowY(index));
    }

    public void applyPage(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, DisplayRow.RowKey anchor) {
        int oldIndex = ResultWindow.indexOf(window.rows(), anchor);
        double screenY = oldIndex < 0 ? 0 : layout.rowY(oldIndex) - scrollY;
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        int newIndex = ResultWindow.indexOf(window.rows(), anchor);
        if (oldIndex >= 0 && newIndex >= 0) {
            this.scrollY = clampScroll(layout.rowY(newIndex) - screenY);
        } else {
            this.scrollY = clampScroll(scrollY);
        }
    }

    /**
     * Shows {@code N matches found} over the list for a moment after a search. Stays while the
     * pointer is over the list, then fades out. Counts above 99 are shown as {@code >99}.
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
    public boolean canFocus(UIComponent.FocusSource source) {
        return true;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int listWidth = Math.max(0, width - TIMELINE_WIDTH);
        if (listWidth != laidOutWidth) {
            laidOutWidth = listWidth;
            rebuildLayout();
        }
        graphics.fill(x, y, x + listWidth, y + height, LIST_BG);
        drawRows(graphics, listWidth);
        drawBanner(graphics, listWidth, mouseX, mouseY);
        drawTimeline(graphics, x + listWidth, mouseX, mouseY);
        updateCursor(mouseX, mouseY, listWidth);
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
            previewScrub(click.y());
            return true;
        }
        int row = rowAtScreenY(click.y());
        if (row < 0) return super.onMouseDown(click, doubled);
        if (doubled) {
            onExpand.accept(window.rows().get(row));
            return true;
        }
        if (click.x() >= messageX()) {
            draggingSelection = true;
            selection.start(row, charAt(row, click.x(), click.y()));
            return true;
        }
        selection.clear();
        return super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingTimeline) {
            previewScrub(click.y());
            return true;
        }
        if (draggingSelection) {
            int row = rowAtScreenY(click.y());
            if (row >= 0) {
                selection.extend(row, charAt(row, click.x(), click.y()));
            }
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (draggingTimeline) {
            commitScrub(click.y());
        }
        draggingTimeline = false;
        draggingSelection = false;
        return super.onMouseUp(click);
    }

    @Override
    public boolean onKeyPress(KeyEvent event) {
        if (event.isCopy() && !selection.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selection.copy(window.rows()));
            return true;
        }
        return super.onKeyPress(event);
    }

    private void rebuildLayout() {
        int messageWidth = messageWidth();
        Font font = Minecraft.getInstance().font;
        layout = MessageListLayout.of(window.rows(), contextLines, messageWidth, font::width);
        this.scrollY = clampScroll(scrollY);
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
            int timestampWidth = font.width("00:00:00  ");
            int messageWidth = messageWidth();
            int first = Math.max(0, layout.rowAtY(scrollY));
            int last = Math.min(rows.size() - 1, layout.rowAtY(scrollY + height) + 1);
            for (int i = first; i <= last; i++) {
                DisplayRow row = rows.get(i);
                int rowY = y + layout.rowY(i) - (int) Math.round(scrollY);
                int msgX = x + 4 + timestampWidth;
                List<String> lines = MessageWrap.lines(row.message(), messageWidth, font::width);
                drawSelection(graphics, font, i, lines, msgX, rowY);
                graphics.drawText(MessageComponents.timestamp(row), x + 4, rowY + 1, 1, MUTED);
                int offset = 0;
                int lineY = rowY;
                for (String line : lines) {
                    graphics.drawText(MessageComponents.messageRange(row, offset, offset + line.length()),
                        msgX, lineY + 1, 1, TEXT);
                    offset += line.length();
                    lineY += ROW_HEIGHT;
                }
            }
            drawDateHeaders(graphics, listWidth);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawSelection(OwoUIGraphics graphics, Font font, int row, List<String> lines, int msgX, int rowY) {
        if (selection.isEmpty()) return;
        int offset = 0;
        int lineY = rowY;
        for (String line : lines) {
            int start = -1;
            for (int i = 0; i <= line.length(); i++) {
                if (i < line.length() && selection.covers(row, offset + i)) {
                    if (start < 0) start = i;
                } else if (start >= 0) {
                    int left = msgX + font.width(line.substring(0, start));
                    int right = msgX + font.width(line.substring(0, i));
                    graphics.fill(left, lineY, right, lineY + ROW_HEIGHT, SELECTION);
                    start = -1;
                }
            }
            offset += line.length();
            lineY += ROW_HEIGHT;
        }
    }

    private void drawDateHeaders(OwoUIGraphics graphics, int listWidth) {
        MessageListLayout.DateBand sticky = layout.stickyAt(scrollY);
        for (MessageListLayout.DateBand band : layout.dates()) {
            int headerY = y + band.y() - (int) Math.round(scrollY);
            if (sticky != null && band == sticky) continue;
            if (headerY + MessageListLayout.DATE_HEIGHT < y || headerY > y + height) continue;
            drawDateBand(graphics, band.date(), headerY, listWidth);
        }
        if (sticky == null) return;
        int headerY = y;
        MessageListLayout.DateBand next = layout.nextDate(sticky);
        if (next != null) {
            int nextScreenY = y + next.y() - (int) Math.round(scrollY);
            if (nextScreenY < headerY + MessageListLayout.DATE_HEIGHT) {
                headerY = nextScreenY - MessageListLayout.DATE_HEIGHT;
            }
        }
        drawDateBand(graphics, sticky.date(), headerY, listWidth);
    }

    private void drawDateBand(OwoUIGraphics graphics, LocalDate date, int headerY, int listWidth) {
        graphics.fill(x, headerY, x + listWidth, headerY + MessageListLayout.DATE_HEIGHT, DATE_BG);
        graphics.drawText(MessageComponents.dateHeader(date), x + 4, headerY + 4, DATE_SCALE, TEXT);
    }

    private void drawBanner(OwoUIGraphics graphics, int listWidth, int mouseX, int mouseY) {
        boolean overList = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + height;
        boolean persistent = !overlayMessage.getString().isEmpty();
        boolean timed = showMatchBanner && (overList || System.currentTimeMillis() < bannerUntilMs);
        if (!persistent && !timed && !loading) {
            showMatchBanner = false;
            return;
        }
        Component text = bannerText(persistent, timed);
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int boxWidth = Math.min(listWidth - 16, textWidth + 16);
        int boxHeight = 16;
        int boxX = x + Math.max(8, listWidth - 8 - boxWidth);
        int boxY = y + height - 8 - boxHeight;
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, BANNER_BG);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, BANNER_BORDER);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, BANNER_BORDER);
        graphics.drawText(text, boxX + 8, boxY + 4, 1, TEXT);
    }

    private Component bannerText(boolean persistent, boolean timed) {
        if (persistent) return overlayMessage;
        boolean showMatches = timed || showMatchBanner;
        if (loading && showMatches) {
            return Component.translatable("allthelogs.status.matches_loading",
                MessageComponents.matchCountText(matchCount));
        }
        if (loading) {
            return Component.translatable("allthelogs.status.loading");
        }
        return Component.translatable("allthelogs.status.matches", MessageComponents.matchCountText(matchCount));
    }

    private void drawTimeline(OwoUIGraphics graphics, int columnX, int mouseX, int mouseY) {
        int trackRight = x + width - 2;
        int trackLeft = trackRight - TRACK_WIDTH;
        int trackX = trackLeft;
        graphics.fill(trackX, y, trackX + TRACK_WIDTH, y + height, TRACK);
        graphics.fill(trackX, y, trackX + 1, y + height, TRACK_BORDER);

        LocalDateTime oldest = rangeOldest;
        LocalDateTime newest = rangeNewest;
        if (oldest == null || newest == null) {
            oldest = window.firstMatchTime();
            newest = window.lastMatchTime();
        }
        if (oldest == null || newest == null) {
            return;
        }

        LocalDateTime viewTime = visibleTime();
        int thumbCenter;
        if (draggingTimeline && !Double.isNaN(scrubY)) {
            thumbCenter = (int) Math.round(scrubY);
        } else if (viewTime != null) {
            thumbCenter = TimelineLayout.yFromNewest(viewTime, oldest, newest, y, height);
        } else {
            thumbCenter = Integer.MIN_VALUE;
        }
        if (thumbCenter != Integer.MIN_VALUE) {
            int thumbTop = Math.clamp(thumbCenter - THUMB_HEIGHT / 2, y, y + height - THUMB_HEIGHT);
            graphics.fill(trackX + 1, thumbTop, trackX + TRACK_WIDTH - 1, thumbTop + THUMB_HEIGHT, THUMB);
        }

        boolean nearTrack = mouseX >= trackX - HOVER_SLOP && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (nearTrack || draggingTimeline) {
            LocalDateTime hoverTime = timeAtY(mouseY);
            if (hoverTime != null) {
                String label = TimelineLayout.hoverLabel(hoverTime, uniqueMatchDates);
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

    private void updateCursor(int mouseX, int mouseY, int listWidth) {
        boolean nearTrack = mouseX >= x + listWidth - HOVER_SLOP && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
        boolean overMessage = mouseX >= messageX() && mouseX < x + listWidth
            && mouseY >= y && mouseY < y + height;
        if (nearTrack || draggingTimeline) {
            this.cursorStyle(CursorStyle.MOVE);
        } else if (overMessage) {
            this.cursorStyle(CursorStyle.TEXT);
        } else {
            this.cursorStyle(CursorStyle.POINTER);
        }
    }

    private LocalDateTime visibleTime() {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) return null;
        int index = Math.clamp(layout.rowAtY(scrollY), 0, rows.size() - 1);
        return rows.get(index).entry().timestamp();
    }

    private LocalDateTime timeAtY(double mouseY) {
        LocalDateTime oldest = rangeOldest != null ? rangeOldest : window.firstMatchTime();
        LocalDateTime newest = rangeNewest != null ? rangeNewest : window.lastMatchTime();
        if (oldest == null || newest == null) return null;
        double progress = (mouseY - y) / Math.max(1, height - 1);
        return TimelineLayout.timeFromNewest(progress, oldest, newest);
    }

    private void previewScrub(double mouseY) {
        scrubY = Math.clamp(mouseY, y, y + Math.max(0, height - 1));
        LocalDateTime time = timeAtY(mouseY);
        if (time == null) return;
        if (window.coversTime(time)) {
            scrollToTime(time);
            maybeRequestMore();
        }
    }

    private void commitScrub(double mouseY) {
        LocalDateTime time = timeAtY(mouseY);
        scrubY = Double.NaN;
        if (time == null) return;
        if (window.coversTime(time)) {
            scrollToTime(time);
            maybeRequestMore();
            return;
        }
        onJump.accept(time);
    }

    private void maybeRequestMore() {
        if (loading || window.rows().isEmpty()) return;
        int firstVisible = firstVisibleIndex();
        int lastVisible = lastVisibleIndex();
        if (window.hasBefore() && firstVisible <= 2) {
            onApproachEdge.accept(Edge.BEFORE);
        } else if (window.hasAfter() && lastVisible >= window.rows().size() - 3) {
            onApproachEdge.accept(Edge.AFTER);
        }
    }

    private int rowAtScreenY(double screenY) {
        if (layout.size() == 0) return -1;
        int contentY = (int) Math.round(screenY - y + scrollY);
        int index = layout.rowAtY(contentY);
        if (index < 0 || index >= window.rows().size()) return -1;
        int top = layout.rowY(index);
        int bottom = top + layout.rowHeight(index);
        if (contentY < top || contentY >= bottom) return -1;
        return index;
    }

    private int messageX() {
        return x + 4 + Minecraft.getInstance().font.width("00:00:00  ");
    }

    private int messageWidth() {
        int listWidth = Math.max(0, width - TIMELINE_WIDTH);
        return Math.max(16, listWidth - 8 - Minecraft.getInstance().font.width("00:00:00  "));
    }

    private int charAt(int row, double screenX, double screenY) {
        List<DisplayRow> rows = window.rows();
        if (row < 0 || row >= rows.size()) return 0;
        String message = rows.get(row).message();
        Font font = Minecraft.getInstance().font;
        int localX = (int) Math.round(screenX - messageX());
        int localY = (int) Math.round(screenY - y + scrollY - layout.rowY(row));
        int line = localY < 0 ? 0 : localY / ROW_HEIGHT;
        return MessageWrap.charIndex(message, messageWidth(), line, localX, font::width);
    }

    private double clampScroll(double value) {
        double max = Math.max(0, layout.contentHeight() - height);
        if (value < 0) return 0;
        if (value > max) return max;
        return value;
    }

    public enum Edge {
        BEFORE, AFTER
    }
}
