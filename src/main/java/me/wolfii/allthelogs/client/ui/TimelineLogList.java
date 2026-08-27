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
import java.time.YearMonth;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

/**
 * Virtualised log list plus a timeline scrubber on the right. Newest is at the top. The scrubber maps the
 * matched-log range only and always shows date ticks along the track, Immich-style.
 */
public final class TimelineLogList extends BaseUIComponent {
    public static final int ROW_HEIGHT = MessageListLayout.ROW_HEIGHT;
    public static final int TIMELINE_WIDTH = 68;
    public static final int SCRUB_PAGE_SIZE = 24;
    private static final int TRACK_WIDTH = 8;
    private static final int THUMB_HEIGHT = 16;
    private static final int BANNER_MS = 5000;
    private static final int HOVER_SLOP = 12;
    private static final int LIST_PAD = 4;
    private static final int SCRUB_THROTTLE_MS = 50;
    private static final int TICK_GAP_PX = 16;
    private static final int INFO_MAX_WIDTH = 240;
    private static final int AUTO_SCROLL_DEADZONE = 8;

    private static final int LIST_BG = 0x80000000;
    private static final float DATE_SCALE = 1.25f;
    private static final int TRACK = 0xFF2B2B2B;
    private static final int TRACK_BORDER = 0xFF3A3A3A;
    private static final int THUMB = 0xD0FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int TICK_LABEL = 0xFF8E8E8E;
    private static final int TICK_DOT = 0xFF9A9A9A;
    private static final int BANNER_BG = 0xE0181818;
    private static final int BANNER_BORDER = 0xFF3C3C3C;
    private static final int HOVER_BG = 0xF01C1C1C;
    private static final int DATE_BG = 0xE0141414;
    private static final int SELECTION = 0x663A6EA5;

    private final ResultWindow window = new ResultWindow();
    private final MessageSelection selection = new MessageSelection();
    private MessageListLayout layout = MessageListLayout.of(List.of(), 0);
    private int contextLines;
    private LocalDateTime boundsOldest;
    private LocalDateTime boundsNewest;
    private int uniqueMatchDates;
    private List<YearMonth> matchMonths = List.of();
    private double scrollY;
    private boolean loading;
    private boolean draggingTimeline;
    private boolean draggingSelection;
    private boolean autoScrolling;
    private double autoScrollOriginY;
    private double autoScrollMouseY;
    private double scrubY = Double.NaN;
    private long lastScrubQueryMs;
    private int laidOutWidth = -1;
    private int matchCount;
    private long matchElapsedMs;
    private boolean showMatchBanner;
    private long bannerUntilMs;
    private Component overlayMessage = Component.empty();
    private Consumer<Edge> onApproachEdge = edge -> {
    };
    private BiConsumer<LocalDateTime, Boolean> onJump = (time, preview) -> {
    };
    private Consumer<DisplayRow> onExpand = row -> {
    };
    private Runnable onScrubBegin = () -> {
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

    public void onJump(BiConsumer<LocalDateTime, Boolean> onJump) {
        this.onJump = onJump;
    }

    public void onExpand(Consumer<DisplayRow> onExpand) {
        this.onExpand = onExpand;
    }

    public void onScrubBegin(Runnable onScrubBegin) {
        this.onScrubBegin = onScrubBegin;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
        rebuildLayout();
    }

    public void setMatchBounds(MatchBounds bounds) {
        this.boundsOldest = bounds == null ? null : bounds.oldest();
        this.boundsNewest = bounds == null ? null : bounds.newest();
        this.uniqueMatchDates = bounds == null ? 0 : bounds.uniqueDates();
        this.matchMonths = bounds == null || bounds.months() == null ? List.of() : bounds.months();
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
        return window.keyAt(firstVisibleIndex());
    }

    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        this.scrollY = 0;
        selection.clear();
        finishScrub();
    }

    /**
     * Replaces the buffered page and scrolls so {@code time} is at the top, without flashing the list to
     * offset 0 first. Used while dragging the timeline so the thumb can stay put.
     */
    public void showAt(LocalDateTime time, List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        scrollToTime(time);
    }

    public void finishScrub() {
        if (draggingTimeline) return;
        scrubY = Double.NaN;
    }

    public void scrollToTime(LocalDateTime time) {
        int index = window.nearestIndex(time);
        if (index < 0) return;
        setScrollY(layout.rowY(index));
    }

    public void applyPage(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, DisplayRow.RowKey anchor) {
        int oldIndex = ResultWindow.indexOf(window.rows(), anchor);
        double oldY = layout.rowY(oldIndex);
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        int newIndex = ResultWindow.indexOf(window.rows(), anchor);
        setScrollY(ResultWindow.keepAnchor(oldIndex, newIndex, oldY, layout.rowY(newIndex), scrollY));
    }

    /**
     * Shows {@code N matches (Xms)} over the list for a moment after a search. Stays while the
     * pointer is over the list, then fades out. Counts above 99 are shown as {@code >99}.
     * While a query is running the chip shows loading instead of the count.
     */
    public void showMatchCount(int matches, long elapsedMs) {
        this.matchCount = matches;
        this.matchElapsedMs = Math.max(0, elapsedMs);
        this.showMatchBanner = true;
        this.bannerUntilMs = System.currentTimeMillis() + BANNER_MS;
        this.overlayMessage = Component.empty();
    }

    public void showMatchCount(int matches) {
        showMatchCount(matches, 0);
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
        int listWidth = listWidth();
        if (listWidth != laidOutWidth) {
            laidOutWidth = listWidth;
            rebuildLayout();
        }
        graphics.fill(x, y, x + listWidth, y + height, LIST_BG);
        applyAutoScroll(mouseY, delta);
        drawRows(graphics, listWidth);
        drawMessageInfo(graphics, mouseX, mouseY, listWidth);
        drawBanner(graphics, listWidth, mouseX, mouseY);
        drawTimeline(graphics, mouseX, mouseY);
        updateCursor(mouseX, mouseY, listWidth);
    }

    /**
     * Mouse events are component-local (0,0 is this list's top-left). Draw calls still use
     * screen coordinates via {@code x}/{@code y}.
     */
    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (overTimelineLocal(mouseX)) return false;
        if (autoScrolling) {
            stopAutoScroll();
            return true;
        }
        setScrollY(scrollY - amount * ROW_HEIGHT * 3);
        maybeRequestMore();
        return true;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (focusHandler() != null) {
            focusHandler().focus(this, UIComponent.FocusSource.MOUSE_CLICK);
        }
        if (overTimelineLocal(click.x())) {
            stopAutoScroll();
            draggingTimeline = true;
            onScrubBegin.run();
            previewScrub(click.y());
            return true;
        }
        if (autoScrolling) {
            stopAutoScroll();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            autoScrolling = true;
            autoScrollOriginY = click.y();
            autoScrollMouseY = click.y();
            draggingSelection = false;
            return true;
        }
        int row = rowAtLocalY(click.y());
        if (row < 0) return super.onMouseDown(click, doubled);
        if (doubled) {
            onExpand.accept(window.rows().get(row));
            return true;
        }
        if (click.x() >= messageLocalX()) {
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
        if (autoScrolling) {
            autoScrollMouseY = click.y();
            return true;
        }
        if (draggingSelection) {
            int row = rowAtLocalY(click.y());
            if (row >= 0) selection.extend(row, charAt(row, click.x(), click.y()));
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (draggingTimeline) {
            draggingTimeline = false;
            commitScrub(click.y());
        }
        draggingSelection = false;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return true;
        }
        return super.onMouseUp(click);
    }

    @Override
    public boolean onKeyPress(KeyEvent event) {
        if (event.isSelectAll()) {
            return selectAllOnVisibleDate();
        }
        if (event.isCopy() && !selection.isEmpty()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selection.copy(window.rows()));
            return true;
        }
        return super.onKeyPress(event);
    }

    public boolean selectAllOnVisibleDate() {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) return false;
        LocalDate date = visibleDate();
        if (date == null) return false;
        selection.selectDate(rows, date);
        return true;
    }

    private void rebuildLayout() {
        layout = MessageListLayout.of(window.rows(), contextLines, messageWidth(), font()::width);
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
            Font font = font();
            int timestampWidth = timestampWidth();
            int messageWidth = messageWidth();
            int first = Math.max(0, layout.rowAtY(scrollY));
            int last = Math.min(rows.size() - 1, layout.rowAtY(scrollY + height) + 1);
            for (int i = first; i <= last; i++) {
                DisplayRow row = rows.get(i);
                int rowY = screenY(layout.rowY(i));
                int msgX = x + LIST_PAD + timestampWidth;
                List<MessageWrap.Line> lines = MessageWrap.wrap(row.message(), messageWidth, font::width);
                drawSelection(graphics, font, i, lines, msgX, rowY);
                graphics.drawText(MessageComponents.timestamp(row), x + LIST_PAD, rowY + 1, 1, MUTED);
                int lineY = rowY;
                for (MessageWrap.Line line : lines) {
                    graphics.drawText(MessageComponents.messageRange(row, line.start(), line.start() + line.text().length()),
                        msgX, lineY + 1, 1, TEXT);
                    lineY += ROW_HEIGHT;
                }
            }
            drawDateHeaders(graphics, listWidth);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawSelection(OwoUIGraphics graphics, Font font, int row, List<MessageWrap.Line> lines, int msgX, int rowY) {
        if (selection.isEmpty()) return;
        int lineY = rowY;
        for (MessageWrap.Line line : lines) {
            String text = line.text();
            int start = -1;
            for (int i = 0; i <= text.length(); i++) {
                boolean covered = i < text.length() && selection.covers(row, line.start() + i);
                if (covered && start < 0) start = i;
                if (!covered && start >= 0) {
                    int left = msgX + font.width(text.substring(0, start));
                    int right = msgX + font.width(text.substring(0, i));
                    graphics.fill(left, lineY, right, lineY + ROW_HEIGHT, SELECTION);
                    start = -1;
                }
            }
            lineY += ROW_HEIGHT;
        }
    }

    private void drawMessageInfo(OwoUIGraphics graphics, int mouseX, int mouseY, int listWidth) {
        if (draggingSelection || draggingTimeline || autoScrolling) return;
        if (mouseX < x + LIST_PAD || mouseX >= messageX() || mouseY < y || mouseY >= y + height) return;
        int row = rowAtLocalY(mouseY - y);
        if (row < 0) return;
        Font font = font();
        int maxTextWidth = Math.min(INFO_MAX_WIDTH, Math.max(48, listWidth - 16));
        List<Component> lines = MessageComponents.messageInfo(window.rows().get(row), maxTextWidth,
            text -> (int) Math.ceil(font.width(text) * 0.85f));
        int lineHeight = 10;
        int pad = 5;
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, (int) Math.ceil(font.width(line) * 0.85f));
        }
        int boxWidth = Math.min(listWidth - 8, textWidth + pad * 2);
        int boxHeight = pad * 2 + lines.size() * lineHeight - 2;
        int rowScreenY = screenY(layout.rowY(row));
        int boxX = Math.clamp(x + LIST_PAD, x, Math.max(x, x + listWidth - boxWidth));
        int boxY = rowScreenY + ROW_HEIGHT + 2;
        if (boxY + boxHeight > y + height) {
            boxY = Math.max(y, rowScreenY - boxHeight - 2);
        }
        fillChip(graphics, boxX, boxY, boxWidth, boxHeight, HOVER_BG);
        int textY = boxY + pad;
        for (Component line : lines) {
            int color = line.getStyle().getColor() == null ? TEXT : (0xFF000000 | line.getStyle().getColor().getValue());
            graphics.drawText(line, boxX + pad, textY, 0.85f, color);
            textY += lineHeight;
        }
    }

    private void drawDateHeaders(OwoUIGraphics graphics, int listWidth) {
        MessageListLayout.DateBand sticky = layout.stickyAt(scrollY);
        for (MessageListLayout.DateBand band : layout.dates()) {
            int headerY = screenY(band.y());
            if (sticky != null && band == sticky) continue;
            if (headerY + MessageListLayout.DATE_HEIGHT < y || headerY > y + height) continue;
            drawDateBand(graphics, band.date(), headerY, listWidth);
        }
        if (sticky == null) return;
        int headerY = y;
        MessageListLayout.DateBand next = layout.nextDate(sticky);
        if (next != null) {
            int nextScreenY = screenY(next.y());
            if (nextScreenY < headerY + MessageListLayout.DATE_HEIGHT) {
                headerY = nextScreenY - MessageListLayout.DATE_HEIGHT;
            }
        }
        drawDateBand(graphics, sticky.date(), headerY, listWidth);
    }

    private void drawDateBand(OwoUIGraphics graphics, LocalDate date, int headerY, int listWidth) {
        graphics.fill(x, headerY, x + listWidth, headerY + MessageListLayout.DATE_HEIGHT, DATE_BG);
        graphics.drawText(MessageComponents.dateHeader(date), x + LIST_PAD, headerY + MessageListLayout.DATE_HEIGHT,
            DATE_SCALE, TEXT, OwoUIGraphics.TextAnchor.BOTTOM_LEFT);
    }

    private void drawBanner(OwoUIGraphics graphics, int listWidth, int mouseX, int mouseY) {
        boolean overList = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + height;
        boolean persistent = !overlayMessage.getString().isEmpty();
        boolean timed = showMatchBanner && (overList || System.currentTimeMillis() < bannerUntilMs);
        if (!persistent && !timed && !loading) {
            showMatchBanner = false;
            return;
        }
        Component text = MessageComponents.listStatus(overlayMessage, loading, timed || showMatchBanner, matchCount,
            matchElapsedMs);
        Font font = font();
        int boxWidth = Math.min(listWidth - 16, font.width(text) + 16);
        int boxHeight = 16;
        int boxX = x + Math.max(8, listWidth - 8 - boxWidth);
        int boxY = y + height - 8 - boxHeight;
        fillChip(graphics, boxX, boxY, boxWidth, boxHeight, BANNER_BG);
        graphics.drawText(text, boxX + 8, boxY + LIST_PAD, 1, TEXT);
    }

    private void drawTimeline(OwoUIGraphics graphics, int mouseX, int mouseY) {
        int trackX = x + width - 3 - TRACK_WIDTH;
        graphics.fill(trackX, y, trackX + TRACK_WIDTH, y + height, TRACK);
        graphics.fill(trackX, y, trackX + 1, y + height, TRACK_BORDER);

        LocalDateTime oldest = scrubOldest();
        LocalDateTime newest = scrubNewest();
        if (oldest == null || newest == null) return;

        drawDateTicks(graphics, trackX, oldest, newest);

        int thumbCenter = thumbCenter(oldest, newest);
        if (thumbCenter != Integer.MIN_VALUE) {
            int thumbTop = Math.clamp(thumbCenter - THUMB_HEIGHT / 2, y, y + height - THUMB_HEIGHT);
            graphics.fill(trackX + 1, thumbTop, trackX + TRACK_WIDTH - 1, thumbTop + THUMB_HEIGHT, THUMB);
        }

        boolean nearTrack = mouseX >= trackX - HOVER_SLOP && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (!nearTrack && !draggingTimeline) return;
        LocalDateTime hoverTime = timeAtLocalY(mouseY - y);
        if (hoverTime == null) return;
        String label = TimelineLayout.hoverLabel(hoverTime, uniqueMatchDates);
        int labelWidth = (int) (font().width(label) * 0.85f) + 10;
        int labelHeight = 14;
        int labelY = Math.clamp(mouseY - labelHeight / 2, y, y + height - labelHeight);
        int labelX = trackX - 6 - labelWidth;
        fillChip(graphics, labelX, labelY, labelWidth, labelHeight, HOVER_BG);
        graphics.drawText(Component.literal(label), labelX + 5, labelY + 3, 0.85f, TEXT);
    }

    private void drawDateTicks(OwoUIGraphics graphics, int trackX, LocalDateTime oldest, LocalDateTime newest) {
        for (TimelineLayout.DateTick tick : TimelineLayout.spacedTicks(oldest, newest, matchMonths, height, TICK_GAP_PX)) {
            int tickY = TimelineLayout.yFromNewest(tick.at(), oldest, newest, matchMonths, y, height);
            graphics.fill(trackX + 2, tickY, trackX + TRACK_WIDTH - 2, tickY + 1, TICK_DOT);
            graphics.drawText(Component.literal(tick.label()), trackX - 3, tickY,
                0.7f, TICK_LABEL, OwoUIGraphics.TextAnchor.BOTTOM_RIGHT);
        }
    }

    private int thumbCenter(LocalDateTime oldest, LocalDateTime newest) {
        if (!Double.isNaN(scrubY)) return y + (int) Math.round(scrubY);
        LocalDateTime viewTime = visibleTime();
        if (viewTime == null) return Integer.MIN_VALUE;
        return TimelineLayout.yFromNewest(viewTime, oldest, newest, matchMonths, y, height);
    }

    private void fillChip(OwoUIGraphics graphics, int boxX, int boxY, int boxWidth, int boxHeight, int fill) {
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, fill);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, BANNER_BORDER);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, BANNER_BORDER);
    }

    private void updateCursor(int mouseX, int mouseY, int listWidth) {
        boolean nearTrack = mouseX >= x + listWidth - HOVER_SLOP && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
        boolean overMessage = mouseX >= messageX() && mouseX < x + listWidth && mouseY >= y && mouseY < y + height;
        if (nearTrack || draggingTimeline || autoScrolling) {
            this.cursorStyle(CursorStyle.MOVE);
        } else if (overMessage) {
            this.cursorStyle(CursorStyle.TEXT);
        } else {
            this.cursorStyle(CursorStyle.POINTER);
        }
    }

    private void applyAutoScroll(int mouseY, float delta) {
        if (!autoScrolling) return;
        autoScrollMouseY = mouseY - y;
        double offset = autoScrollMouseY - autoScrollOriginY;
        if (Math.abs(offset) <= AUTO_SCROLL_DEADZONE) return;
        double signed = offset - Math.copySign(AUTO_SCROLL_DEADZONE, offset);
        double speed = signed / 28.0;
        setScrollY(scrollY + speed * ROW_HEIGHT * Math.max(0.05, delta) * 8);
        maybeRequestMore();
    }

    private void stopAutoScroll() {
        autoScrolling = false;
    }

    private LocalDateTime visibleTime() {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) return null;
        int index = Math.clamp(layout.rowAtY(scrollY), 0, rows.size() - 1);
        return rows.get(index).entry().timestamp();
    }

    private LocalDate visibleDate() {
        MessageListLayout.DateBand sticky = layout.stickyAt(scrollY);
        if (sticky != null) return sticky.date();
        LocalDateTime time = visibleTime();
        return time == null ? null : time.toLocalDate();
    }

    private LocalDateTime timeAtLocalY(double localY) {
        LocalDateTime oldest = scrubOldest();
        LocalDateTime newest = scrubNewest();
        if (oldest == null || newest == null) return null;
        double progress = localY / Math.max(1, height - 1);
        return TimelineLayout.timeFromNewest(progress, oldest, newest, matchMonths);
    }

    private LocalDateTime scrubOldest() {
        return boundsOldest != null ? boundsOldest : window.firstMatchTime();
    }

    private LocalDateTime scrubNewest() {
        return boundsNewest != null ? boundsNewest : window.lastMatchTime();
    }

    private void previewScrub(double localY) {
        scrubY = Math.clamp(localY, 0, Math.max(0, height - 1));
        applyScrub(scrubY, false);
    }

    private void commitScrub(double localY) {
        applyScrub(Math.clamp(localY, 0, Math.max(0, height - 1)), true);
    }

    private void applyScrub(double localY, boolean commit) {
        LocalDateTime time = timeAtLocalY(localY);
        if (time == null) return;
        if (window.coversTime(time)) {
            scrollToTime(time);
            if (!draggingTimeline) maybeRequestMore();
            if (commit) finishScrub();
            return;
        }
        if (!commit) {
            long now = System.currentTimeMillis();
            if (now - lastScrubQueryMs < SCRUB_THROTTLE_MS) return;
            lastScrubQueryMs = now;
        }
        onJump.accept(time, !commit);
    }

    private void maybeRequestMore() {
        if (loading || draggingTimeline || window.rows().isEmpty()) return;
        int firstVisible = firstVisibleIndex();
        int lastVisible = lastVisibleIndex();
        if (window.hasBefore() && firstVisible <= 2) {
            onApproachEdge.accept(Edge.BEFORE);
        } else if (window.hasAfter() && lastVisible >= window.rows().size() - 3) {
            onApproachEdge.accept(Edge.AFTER);
        }
    }

    private int rowAtLocalY(double localY) {
        if (layout.size() == 0) return -1;
        int contentY = (int) Math.round(localY + scrollY);
        int index = layout.rowAtY(contentY);
        if (index < 0 || index >= window.rows().size()) return -1;
        int top = layout.rowY(index);
        if (contentY < top || contentY >= top + layout.rowHeight(index)) return -1;
        return index;
    }

    private int screenY(int contentY) {
        return y + contentY - (int) Math.round(scrollY);
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private int listWidth() {
        return Math.max(0, width - TIMELINE_WIDTH);
    }

    private int timestampWidth() {
        return font().width(MessageComponents.TIMESTAMP_GUTTER);
    }

    private int messageX() {
        return x + messageLocalX();
    }

    private int messageLocalX() {
        return LIST_PAD + timestampWidth();
    }

    private int messageWidth() {
        return Math.max(16, listWidth() - LIST_PAD * 2 - timestampWidth());
    }

    private boolean overTimelineLocal(double localX) {
        return localX >= width - TIMELINE_WIDTH;
    }

    private int charAt(int row, double localX, double localY) {
        List<DisplayRow> rows = window.rows();
        if (row < 0 || row >= rows.size()) return 0;
        int xInMessage = (int) Math.round(localX - messageLocalX());
        int yInRow = (int) Math.round(localY + scrollY - layout.rowY(row));
        int line = yInRow < 0 ? 0 : yInRow / ROW_HEIGHT;
        return MessageWrap.charIndex(rows.get(row).message(), messageWidth(), line, xInMessage, font()::width);
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
