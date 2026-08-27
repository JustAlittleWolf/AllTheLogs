package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.list.*;
import me.wolfii.allthelogs.client.timeline.TimelineLayout;
import me.wolfii.allthelogs.client.ui.text.MessageText;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.data.MatchDay;
import me.wolfii.allthelogs.data.MatchSummary;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Virtualised log list plus a timeline scrubber on the right. Newest is at the bottom. The scrubber maps the
 * matched-log range only, keeps a small thumb sized from how many match days are in the query, and always
 * shows date ticks along the track. Short pages sit on the bottom edge of the list.
 */
public final class MessageTimeline extends BaseUIComponent {
    public static final int ROW_HEIGHT = MessageListLayout.ROW_HEIGHT;
    public static final int TIMELINE_WIDTH = 68;
    public static final int SCRUB_PAGE_SIZE = 32;
    private static final int TRACK_WIDTH = 8;
    private static final int MIN_THUMB_HEIGHT = 16;
    private static final int BANNER_MS = 5000;
    private static final int HOVER_SLOP = 12;
    private static final int LIST_PAD = 4;
    private static final int SCRUB_THROTTLE_MS = 100;
    private static final int TICK_GAP_PX = 16;
    private static final int INFO_MAX_WIDTH = 160;
    private static final int AUTO_SCROLL_DEADZONE = 8;
    private static final int MIDDLE_HOLD_MS = 250;
    private static final int LOADING_CHIP_MS = 100;
    private static final int SELECT_DRAG_SLOP = 3;
    private static final int HIGHLIGHT_PAD_LEFT = 1;
    private static final int HIGHLIGHT_TRIM_BOTTOM = 2;
    private static final int TICK_LABEL_OFFSET = 5;

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
    private List<LocalDate> matchDays = List.of();
    private List<MatchDay> matchDayStats = List.of();
    private double scrollY;
    private boolean loading;
    private boolean draggingTimeline;
    private boolean draggingSelection;
    private boolean autoScrolling;
    private long autoScrollDownAtMs;
    private double autoScrollOriginY;
    private double autoScrollMouseY;
    private boolean clickSelectsRow;
    private boolean middleButtonDown;
    private boolean middleHoldMode;
    private int clickRow;
    private DisplayRow.RowKey clickKey;
    private int clickChar;
    private double clickX;
    private double clickY;
    private long loadingSinceMs;
    private double scrubY = Double.NaN;
    private double dragThumbTop = Double.NaN;
    private double thumbGrabOffset;
    private int scrubThumbHeight;
    private long lastScrubQueryMs;
    private boolean scrubQueryInFlight;
    private ScrubJump lastSentScrubJump;
    private int laidOutWidth = -1;
    private DisplayRow cachedWidthRow;
    private Font cachedWidthFont;
    private MessageWrap.RangeWidth cachedRangeWidth;
    private long matchCount;
    private boolean exactMatchCount;
    private long matchElapsedMs;
    private boolean showMatchBanner;
    private long bannerUntilMs;
    private Component overlayMessage = Component.empty();
    private Consumer<Edge> onApproachEdge = edge -> {
    };
    private BiConsumer<ScrubJump, Boolean> onJump = (jump, preview) -> {
    };
    private BiConsumer<DisplayRow, Edge> onExpand = (row, side) -> {
    };
    private Runnable onScrubBegin = () -> {
    };

    public MessageTimeline() {
        this.sizing(Sizing.fill(), Sizing.fill());
        this.cursorStyle(CursorStyle.POINTER);
    }

    /**
     * Once the middle button has been held for {@link #MIDDLE_HOLD_MS}, releasing it should stop auto-scroll.
     */
    static boolean latchMiddleHold(boolean alreadyLatched, boolean buttonDown, long heldMs) {
        return alreadyLatched || (buttonDown && heldMs >= MIDDLE_HOLD_MS);
    }

    /**
     * Preview jumps while the thumb is held: at most one in-flight store query, at least
     * {@code throttleMs} between requests. A later position still fires after the wait so a
     * parked thumb can catch up.
     */
    static boolean shouldSendPreviewScrubQuery(boolean inFlight, long nowMs, long lastQueryMs,
                                               int throttleMs, ScrubJump requested, ScrubJump lastSent) {
        if (inFlight || nowMs - lastQueryMs < throttleMs) return false;
        return !sameScrubTarget(requested, lastSent);
    }

    static boolean sameScrubTarget(ScrubJump left, ScrubJump right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.skip() == right.skip()
            && Double.compare(left.progress(), right.progress()) == 0
            && Objects.equals(left.time(), right.time());
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

    public void onJump(BiConsumer<ScrubJump, Boolean> onJump) {
        this.onJump = onJump;
    }

    public void onExpand(BiConsumer<DisplayRow, Edge> onExpand) {
        this.onExpand = onExpand;
    }

    public void onScrubBegin(Runnable onScrubBegin) {
        this.onScrubBegin = onScrubBegin;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
        rebuildLayout();
    }

    public void setMatchSummary(MatchSummary summary) {
        this.boundsOldest = summary == null ? null : summary.oldest();
        this.boundsNewest = summary == null ? null : summary.newest();
        this.uniqueMatchDates = summary == null ? 0 : summary.uniqueDates();
        this.matchDays = summary == null ? List.of() : summary.dates();
        this.matchDayStats = summary == null || summary.days() == null ? List.of() : summary.days();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        this.loadingSinceMs = loading ? System.currentTimeMillis() : 0;
    }

    public void scrubQueryFinished() {
        scrubQueryInFlight = false;
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
        return Math.max(0, layout.rowAtY(scrollY - contentOrigin()));
    }

    public int lastVisibleIndex() {
        if (layout.size() == 0) return 0;
        return Math.max(firstVisibleIndex(), layout.rowAtY(scrollY - contentOrigin() + Math.max(0, height)));
    }

    public DisplayRow.RowKey visibleAnchor() {
        return window.keyAt(firstVisibleIndex());
    }

    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        this.scrollY = 0;
        selection.clear();
        clickKey = null;
        clickRow = -1;
        draggingSelection = false;
        clickSelectsRow = false;
        finishScrub();
    }

    public void scrollToEnd() {
        setScrollY(Math.max(0, layout.contentHeight() - height));
    }

    /**
     * Re-applies a previously loaded page after the widget is rebuilt (resize or focus), keeping scroll.
     */
    public void restore(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, double scrollY) {
        List<DisplayRow> previous = window.rows();
        window.reset(rows, hasBefore, hasAfter);
        remapSelection(previous, window.rows());
        rebuildLayout();
        this.scrollY = clampScroll(scrollY);
        finishScrub();
    }

    public long matchCount() {
        return matchCount;
    }

    public boolean exactMatchCount() {
        return exactMatchCount;
    }

    public long matchElapsedMs() {
        return matchElapsedMs;
    }

    /**
     * Replaces the buffered page and scrolls so {@code time} is at the top, without flashing the list to
     * offset 0 first. Used while dragging the timeline so the thumb can stay put.
     */
    public void showAt(LocalDateTime time, List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        showAt(time, rows, hasBefore, hasAfter, Double.NaN);
    }

    public void showAt(LocalDateTime time, List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, double progress) {
        List<DisplayRow> previous = window.rows();
        window.reset(rows, hasBefore, hasAfter);
        remapSelection(previous, window.rows());
        rebuildLayout();
        if (!Double.isNaN(progress)) {
            scrollToScrubProgress(progress);
        } else {
            scrollToTime(time);
        }
        if (!draggingTimeline) maybeRequestMore();
    }

    public void scrollToTime(LocalDateTime time) {
        int index = window.nearestIndex(time);
        if (index < 0) return;
        int header = contentOrigin() > 0 ? 0 : MessageListLayout.DATE_HEIGHT;
        setScrollY(TimelineLayout.scrollToRow(layout.rowY(index) - header, layout.contentHeight(), height));
    }

    private void scrollToScrubProgress(double progress) {
        double clamped = Math.clamp(progress, 0, 1);
        if (clamped <= 0) {
            setScrollY(0);
            return;
        }
        if (clamped >= 1) {
            scrollToEnd();
            return;
        }
        if (!scrollLocally(clamped, timeAtProgress(clamped), -1)) {
            scrollToTime(timeAtProgress(clamped));
        }
    }

    public void finishScrub() {
        if (draggingTimeline) return;
        scrubY = Double.NaN;
        dragThumbTop = Double.NaN;
        thumbGrabOffset = 0;
        scrubThumbHeight = 0;
    }

    public void applyPage(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, DisplayRow.RowKey anchor) {
        int oldIndex = ResultWindow.indexOf(window.rows(), anchor);
        double oldY = layout.rowY(oldIndex);
        int oldOrigin = contentOrigin();
        List<DisplayRow> previous = window.rows();
        window.reset(rows, hasBefore, hasAfter);
        remapSelection(previous, window.rows());
        rebuildLayout();
        int newIndex = ResultWindow.indexOf(window.rows(), anchor);
        setScrollY(ResultWindow.keepAnchor(oldIndex, newIndex, oldY, layout.rowY(newIndex), scrollY,
            oldOrigin, contentOrigin()));
        maybeRequestMore();
    }

    public int viewHeight() {
        return height;
    }

    /**
     * Shows {@code N match(es) (Xms)} over the list for a moment after a search. Stays while the
     * pointer is over the list, then fades out. Counts above 99 are shown as {@code >99} until
     * {@link #setTotalMatchCount(long)} supplies the exact total.
     * While a query is running the chip shows loading instead of the count.
     */
    public void showMatchCount(long matches, long elapsedMs) {
        this.matchCount = Math.max(0, matches);
        this.exactMatchCount = matches <= 99;
        this.matchElapsedMs = Math.max(0, elapsedMs);
        this.showMatchBanner = true;
        this.bannerUntilMs = System.currentTimeMillis() + BANNER_MS;
        this.overlayMessage = Component.empty();
    }

    public void showMatchCount(long matches) {
        showMatchCount(matches, 0);
    }

    public void setTotalMatchCount(long total) {
        setTotalMatchCount(total, matchElapsedMs);
    }

    /**
     * Replaces the capped {@code >99} label with the exact total from {@code matches}.
     */
    public void setTotalMatchCount(long total, long elapsedMs) {
        this.matchCount = Math.max(0, total);
        this.exactMatchCount = true;
        this.matchElapsedMs = Math.max(0, elapsedMs);
        this.showMatchBanner = true;
        this.bannerUntilMs = System.currentTimeMillis() + BANNER_MS;
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
        if (draggingTimeline) {
            continueScrub();
        }
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
            int thumb = thumbHeight();
            draggingTimeline = true;
            if (thumb <= 0) {
                scrubThumbHeight = 0;
                thumbGrabOffset = 0;
            } else {
                scrubThumbHeight = Math.max(MIN_THUMB_HEIGHT, thumb);
                int thumbTop = thumbTop(scrubThumbHeight) - y;
                thumbGrabOffset = TimelineLayout.thumbGrabOffset(click.y(), thumbTop, scrubThumbHeight, height);
            }
            onScrubBegin.run();
            lastSentScrubJump = null;
            previewScrub(click.y());
            return true;
        }
        if (autoScrolling) {
            stopAutoScroll();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            autoScrolling = true;
            middleButtonDown = true;
            middleHoldMode = false;
            autoScrollDownAtMs = System.currentTimeMillis();
            autoScrollOriginY = click.y();
            autoScrollMouseY = click.y();
            draggingSelection = false;
            clickSelectsRow = false;
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            selection.clear();
            clickSelectsRow = false;
            draggingSelection = false;
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.onMouseDown(click, doubled);
        }
        int row = rowAtLocalY(click.y());
        if (row < 0) return super.onMouseDown(click, doubled);
        if (doubled) {
            clickSelectsRow = false;
            draggingSelection = false;
            selection.clear();
            onExpand.accept(window.rows().get(row), expandSide(row, click.y()));
            return true;
        }
        clickSelectsRow = true;
        clickRow = row;
        clickKey = window.rows().get(row).key();
        clickChar = charAt(row, click.x(), click.y());
        clickX = click.x();
        clickY = click.y();
        draggingSelection = false;
        return true;
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
        if (clickSelectsRow && movedPastSelectSlop(click.x(), click.y())) {
            clickSelectsRow = false;
            draggingSelection = true;
            selection.start(clickRow, clickChar);
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
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && clickSelectsRow
            && clickRow >= 0 && clickRow < window.rows().size()) {
            selection.selectRow(clickRow, window.rows().get(clickRow).message().length());
        }
        draggingSelection = false;
        clickSelectsRow = false;
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

    private void remapSelection(List<DisplayRow> previous, List<DisplayRow> next) {
        selection.retainIn(previous, next);
        if (clickKey == null) return;
        int index = ResultWindow.indexOf(next, clickKey);
        if (index < 0) {
            clickSelectsRow = false;
            draggingSelection = false;
            clickRow = -1;
            clickKey = null;
            return;
        }
        clickRow = index;
    }

    private void rebuildLayout() {
        layout = MessageListLayout.of(window.rows(), contextLines, messageWidth(), this::messageRangeWidth);
        this.scrollY = clampScroll(scrollY);
    }

    private void drawRows(OwoUIGraphics graphics, int listWidth) {
        List<DisplayRow> rows = window.rows();
        graphics.enableScissor(x, y, x + listWidth, y + height);
        try {
            if (rows.isEmpty()) {
                if (!showingLoading()) {
                    graphics.drawText(Component.translatable("allthelogs.status.empty"), x + 8, y + 8, 1, MUTED);
                }
                return;
            }
            int timestampWidth = timestampWidth();
            int messageWidth = messageWidth();
            int origin = contentOrigin();
            int first = Math.max(0, layout.rowAtY(scrollY - origin));
            int last = Math.min(rows.size() - 1, layout.rowAtY(scrollY - origin + height) + 1);
            for (int i = first; i <= last; i++) {
                DisplayRow row = rows.get(i);
                int rowY = screenY(layout.rowY(i));
                int msgX = x + LIST_PAD + timestampWidth;
                List<MessageWrap.Line> lines = MessageWrap.wrap(row.message(), messageWidth, rangeWidth(row));
                drawHighlights(graphics, row, lines, msgX, rowY);
                drawSelection(graphics, row, i, lines, msgX, rowY);
                int timestampColor = row.match() ? MUTED : Colors.CONTEXT_TIMESTAMP;
                graphics.drawText(MessageText.timestamp(row), x + LIST_PAD, rowY + 1, 1, timestampColor);
                int lineY = rowY;
                for (MessageWrap.Line line : lines) {
                    graphics.drawText(MessageText.messageRange(row, line.start(), line.start() + line.text().length()),
                        msgX, lineY + 1, 1, TEXT);
                    lineY += ROW_HEIGHT;
                }
            }
            drawDateHeaders(graphics, listWidth);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawHighlights(OwoUIGraphics graphics, DisplayRow row,
                                List<MessageWrap.Line> lines, int msgX, int rowY) {
        if (!row.match() || row.highlights().isEmpty()) return;
        int lineY = rowY;
        for (MessageWrap.Line line : lines) {
            int lineStart = line.start();
            int lineEnd = lineStart + line.text().length();
            for (HighlightSpan span : row.highlights()) {
                int from = Math.max(span.start(), lineStart);
                int to = Math.min(span.end(), lineEnd);
                if (from >= to) continue;
                int left = highlightLeft(msgX + messageRangeWidth(row, lineStart, from));
                int right = msgX + messageRangeWidth(row, lineStart, to);
                graphics.fill(left, lineY, right, lineY + highlightHeight(), Colors.MATCH_HIGHLIGHT);
            }
            lineY += ROW_HEIGHT;
        }
    }

    private void drawSelection(OwoUIGraphics graphics, DisplayRow row, int rowIndex,
                               List<MessageWrap.Line> lines, int msgX, int rowY) {
        if (selection.isEmpty()) return;
        int lineY = rowY;
        for (MessageWrap.Line line : lines) {
            String text = line.text();
            int start = -1;
            for (int i = 0; i <= text.length(); i++) {
                boolean covered = i < text.length() && selection.covers(rowIndex, line.start() + i);
                if (covered && start < 0) start = i;
                if (!covered && start >= 0) {
                    int left = msgX + messageRangeWidth(row, line.start(), line.start() + start);
                    int right = msgX + messageRangeWidth(row, line.start(), line.start() + i);
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
        List<Component> lines = MessageText.messageInfo(window.rows().get(row), maxTextWidth,
            text -> (int) Math.ceil(font.width(text) * MessageText.INFO_SCALE));
        int lineHeight = Math.max(8, Math.round(font.lineHeight * MessageText.INFO_SCALE) + 1);
        int pad = 5;
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, (int) Math.ceil(font.width(line) * MessageText.INFO_SCALE));
        }
        int boxWidth = Math.min(listWidth - 8, textWidth + pad * 2);
        int boxHeight = pad * 2 + lines.size() * lineHeight - 2;
        int rowScreenY = screenY(layout.rowY(row));
        int boxX = Math.clamp(x + LIST_PAD, x, Math.max(x, x + listWidth - boxWidth));
        int boxY = rowScreenY + ROW_HEIGHT + 2;
        if (boxY + boxHeight > y + height) {
            boxY = Math.max(y, rowScreenY - boxHeight - 2);
        }
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, HOVER_BG);
        int textY = boxY + pad;
        for (Component line : lines) {
            int color = line.getStyle().getColor() == null ? TEXT : (0xFF000000 | line.getStyle().getColor().getValue());
            graphics.drawText(line, boxX + pad, textY, MessageText.INFO_SCALE, color);
            textY += lineHeight;
        }
    }

    private void drawDateHeaders(OwoUIGraphics graphics, int listWidth) {
        if (contentOrigin() > 0) {
            for (MessageListLayout.DateBand band : layout.dates()) {
                int headerY = screenY(band.y());
                if (headerY + MessageListLayout.DATE_HEIGHT < y || headerY > y + height) continue;
                drawDateBand(graphics, band.date(), headerY, listWidth);
            }
            return;
        }
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
        graphics.drawText(MessageText.dateHeader(date), x + LIST_PAD, headerY + MessageListLayout.DATE_HEIGHT,
            DATE_SCALE, TEXT, OwoUIGraphics.TextAnchor.BOTTOM_LEFT);
    }

    private void drawBanner(OwoUIGraphics graphics, int listWidth, int mouseX, int mouseY) {
        boolean overList = mouseX >= x && mouseX < x + listWidth && mouseY >= y && mouseY < y + height;
        boolean persistent = !overlayMessage.getString().isEmpty();
        boolean timed = showMatchBanner && (overList || System.currentTimeMillis() < bannerUntilMs);
        boolean showLoading = showingLoading();
        if (!persistent && !timed && !showLoading) {
            showMatchBanner = false;
            return;
        }
        Component text = MessageText.listStatus(overlayMessage, showLoading, timed || showMatchBanner, matchCount,
            exactMatchCount, matchElapsedMs);
        Font font = font();
        int boxWidth = Math.min(listWidth - 16, font.width(text) + 16);
        int boxHeight = MessageListLayout.DATE_HEIGHT;
        int boxX = x + Math.max(8, listWidth - 8 - boxWidth);
        int boxY = y;
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, BANNER_BG);
        int textY = boxY + Math.max(0, (boxHeight - font.lineHeight) / 2) + 2;
        graphics.drawText(text, boxX + 8, textY, 1, TEXT);
    }

    private void drawTimeline(OwoUIGraphics graphics, int mouseX, int mouseY) {
        int trackX = x + width - 3 - TRACK_WIDTH;
        graphics.fill(trackX, y, trackX + TRACK_WIDTH, y + height, TRACK);
        graphics.fill(trackX, y, trackX + 1, y + height, TRACK_BORDER);

        LocalDateTime oldest = scrubOldest();
        LocalDateTime newest = scrubNewest();
        if (oldest == null || newest == null) return;

        drawDateTicks(graphics, trackX, oldest, newest);

        int thumbHeight = thumbHeight();
        if (thumbHeight > 0) {
            int thumbTop = thumbTop(thumbHeight);
            graphics.fill(trackX + 1, thumbTop, trackX + TRACK_WIDTH - 1, thumbTop + thumbHeight, THUMB);
        }

        boolean nearTrack = mouseX >= trackX - HOVER_SLOP && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (!nearTrack && !draggingTimeline) return;
        LocalDateTime hoverTime = timeAtLocalY(mouseY - y);
        if (hoverTime == null) return;
        String label = TimelineLayout.hoverLabel(hoverTime, uniqueMatchDates);
        Font font = font();
        int padX = 5;
        int labelWidth = font.width(label) + padX * 2;
        int labelHeight = Math.max(14, font.lineHeight + 6);
        int labelY = Math.clamp(mouseY - labelHeight / 2, y, y + height - labelHeight);
        int labelX = trackX - 6 - labelWidth;
        HoverChip.fill(graphics, labelX, labelY, labelWidth, labelHeight, HOVER_BG);
        int textY = labelY + Math.max(0, (labelHeight - font.lineHeight) / 2);
        graphics.drawText(Component.literal(label), labelX + padX, textY, 1.0f, TEXT);
    }

    private void drawDateTicks(OwoUIGraphics graphics, int trackX, LocalDateTime oldest, LocalDateTime newest) {
        for (TimelineLayout.DateTick tick : TimelineLayout.spacedTicks(oldest, newest, matchDays, height, TICK_GAP_PX)) {
            int tickY;
            if (!matchDayStats.isEmpty()) {
                double progress = TimelineLayout.matchDayProgress(tick.at(), matchDayStats, 0);
                tickY = y + (int) Math.round(progress * Math.max(0, height - 1));
            } else {
                tickY = TimelineLayout.yFromOldest(tick.at(), oldest, newest, matchDays, y, height);
            }
            graphics.fill(trackX + 2, tickY, trackX + TRACK_WIDTH - 2, tickY + 1, TICK_DOT);
            graphics.drawText(Component.literal(tick.label()), trackX - 3, tickY + TICK_LABEL_OFFSET,
                0.75f, TICK_LABEL, OwoUIGraphics.TextAnchor.BOTTOM_RIGHT);
        }
    }

    private int thumbHeight() {
        if (draggingTimeline && scrubThumbHeight > 0) return scrubThumbHeight;
        if (window.rows().isEmpty() || height <= 0) return 0;
        int days = uniqueMatchDates > 0 ? uniqueMatchDates : matchDays.size();
        return TimelineLayout.thumbHeightForDays(height, days, layout.contentHeight(), height);
    }

    private int thumbTop(int thumbHeight) {
        if (!Double.isNaN(dragThumbTop)) {
            return y + (int) Math.round(Math.clamp(dragThumbTop, 0, Math.max(0, height - thumbHeight)));
        }
        if (!Double.isNaN(scrubY)) {
            int center = y + (int) Math.round(Math.clamp(scrubY, 0, Math.max(0, height - 1)));
            return Math.clamp(center - Math.max(1, thumbHeight) / 2, y, y + Math.max(0, height - thumbHeight));
        }
        LocalDateTime time = visibleTime();
        LocalDateTime oldest = scrubOldest();
        LocalDateTime newest = scrubNewest();
        if (time == null || oldest == null || newest == null) return y;
        double progress = TimelineLayout.pinnedProgress(thumbProgress(time), scrolledToStart(), scrolledToEnd());
        return y + TimelineLayout.thumbOffset(height, progress, thumbHeight);
    }

    private boolean scrolledToStart() {
        return !window.hasBefore() && scrollY <= 0.5 && layout.contentHeight() > height;
    }

    private boolean scrolledToEnd() {
        if (window.hasAfter()) return false;
        double max = Math.max(0, layout.contentHeight() - height);
        return scrollY >= max - 0.5;
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
        if (middleButtonDown) {
            boolean pressed = middleMousePressed();
            long heldMs = System.currentTimeMillis() - autoScrollDownAtMs;
            middleHoldMode = latchMiddleHold(middleHoldMode, pressed, heldMs);
            if (!pressed) {
                middleButtonDown = false;
                if (middleHoldMode) {
                    stopAutoScroll();
                    return;
                }
            }
        }
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
        middleButtonDown = false;
        middleHoldMode = false;
    }

    private boolean middleMousePressed() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }

    private boolean showingLoading() {
        return loading && loadingSinceMs > 0 && System.currentTimeMillis() - loadingSinceMs >= LOADING_CHIP_MS;
    }

    private boolean movedPastSelectSlop(double mouseX, double mouseY) {
        double dx = mouseX - clickX;
        double dy = mouseY - clickY;
        return dx * dx + dy * dy >= SELECT_DRAG_SLOP * SELECT_DRAG_SLOP;
    }

    private LocalDateTime visibleTime() {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) return null;
        int header = contentOrigin() > 0 ? 0 : MessageListLayout.DATE_HEIGHT;
        int index = Math.clamp(layout.rowAtY(scrollY - contentOrigin() + header), 0, rows.size() - 1);
        return rows.get(index).entry().timestamp();
    }

    private LocalDate visibleDate() {
        MessageListLayout.DateBand sticky = layout.stickyAt(scrollY);
        if (sticky != null) return sticky.date();
        LocalDateTime time = visibleTime();
        return time == null ? null : time.toLocalDate();
    }

    private LocalDateTime timeAtLocalY(double localY) {
        double progress = localY / Math.max(1, height - 1);
        return timeAtProgress(progress);
    }

    private LocalDateTime timeAtProgress(double progress) {
        if (!matchDayStats.isEmpty()) {
            return TimelineLayout.timeFromMatchDays(progress, matchDayStats);
        }
        LocalDateTime oldest = scrubOldest();
        LocalDateTime newest = scrubNewest();
        if (oldest == null || newest == null) return null;
        return TimelineLayout.timeFromOldest(progress, oldest, newest, matchDays);
    }

    private double thumbProgress(LocalDateTime time) {
        if (!matchDayStats.isEmpty()) {
            return TimelineLayout.matchDayProgress(time, matchDayStats, collapsedFraction(time.toLocalDate()));
        }
        return TimelineLayout.progress(time, scrubOldest(), scrubNewest(), matchDays);
    }

    private double collapsedFraction(LocalDate date) {
        MessageListLayout.DateBand band = layout.dateBand(date);
        if (band == null) return 0;
        int start = band.y();
        int end = layout.dateEndY(band);
        int travel = Math.max(0, end - start - Math.max(0, height));
        if (travel <= 0) {
            double max = Math.max(0, layout.contentHeight() - height);
            if (max <= 0) return 0;
            return Math.clamp(scrollY / max, 0, 1);
        }
        return Math.clamp((scrollY - start) / (double) travel, 0, 1);
    }

    private LocalDateTime scrubOldest() {
        return boundsOldest != null ? boundsOldest : window.firstMatchTime();
    }

    private LocalDateTime scrubNewest() {
        return boundsNewest != null ? boundsNewest : window.lastMatchTime();
    }

    private void previewScrub(double localY) {
        applyThumbScrub(localY, false);
    }

    private void commitScrub(double localY) {
        applyThumbScrub(localY, true);
    }

    private void applyThumbScrub(double localY, boolean commit) {
        int thumbHeight = draggingTimeline && scrubThumbHeight > 0 ? scrubThumbHeight : thumbHeight();
        double progress;
        if (thumbHeight <= 0 || thumbHeight >= height) {
            scrubY = Math.clamp(localY, 0, Math.max(0, height - 1));
            dragThumbTop = Double.NaN;
            progress = scrubY / Math.max(1, height - 1);
        } else {
            double top = Math.clamp(localY - thumbGrabOffset, 0, height - thumbHeight);
            dragThumbTop = top;
            scrubY = Double.NaN;
            progress = TimelineLayout.progressFromThumb((int) Math.round(top), height, thumbHeight);
        }
        applyScrub(progress, commit);
    }

    private void continueScrub() {
        if (!Double.isNaN(dragThumbTop) && scrubThumbHeight > 0 && scrubThumbHeight < height) {
            double progress = TimelineLayout.progressFromThumb((int) Math.round(dragThumbTop), height, scrubThumbHeight);
            applyScrub(progress, false);
            return;
        }
        if (!Double.isNaN(scrubY)) {
            applyScrub(scrubY / Math.max(1, height - 1), false);
        }
    }

    private void applyScrub(double progress, boolean commit) {
        double clamped = Math.clamp(progress, 0, 1);
        if (clamped <= 0) {
            setScrollY(0);
            if (window.hasBefore()) {
                jump(new ScrubJump(scrubOldest(), 0, 0), commit);
                return;
            }
            if (commit) finishScrub();
            return;
        }
        if (clamped >= 1) {
            if (window.hasAfter()) {
                jump(new ScrubJump(scrubNewest(), skipAtEnd(), 1), commit);
                return;
            }
            scrollToEnd();
            if (commit) finishScrub();
            return;
        }
        LocalDateTime time = timeAtProgress(clamped);
        long skip = matchDayStats.isEmpty() ? -1 : TimelineLayout.skipFromProgress(clamped, matchDayStats);
        if (scrollLocally(clamped, time, skip)) {
            if (!draggingTimeline) maybeRequestMore();
            if (commit) finishScrub();
            return;
        }
        jump(new ScrubJump(time, skip, clamped), commit);
    }

    private long skipAtEnd() {
        if (matchDayStats.isEmpty()) return -1;
        return TimelineLayout.skipFromProgress(1, matchDayStats);
    }

    private boolean scrollLocally(double progress, LocalDateTime time, long skip) {
        MatchDay day = TimelineLayout.dayAtProgress(progress, matchDayStats);
        if (day != null) {
            MessageListLayout.DateBand band = layout.dateBand(day.date());
            if (band != null && !(day.collapsed() && skip >= 0 && day.matches() > window.matchCount())) {
                double fraction = TimelineLayout.fractionInDay(progress, matchDayStats);
                setScrollY(TimelineLayout.scrollForDateFraction(band.y(), layout.dateEndY(band), height, fraction));
                return true;
            }
        }
        if (time != null && window.showsDate(time)) {
            scrollToTime(time);
            return true;
        }
        return false;
    }

    private void jump(ScrubJump jump, boolean commit) {
        if (jump.time() == null && jump.skip() < 0) return;
        if (!commit) {
            long now = System.currentTimeMillis();
            if (!shouldSendPreviewScrubQuery(scrubQueryInFlight, now, lastScrubQueryMs,
                SCRUB_THROTTLE_MS, jump, lastSentScrubJump)) {
                return;
            }
            lastScrubQueryMs = now;
            lastSentScrubJump = jump;
            scrubQueryInFlight = true;
        } else {
            lastSentScrubJump = jump;
        }
        onJump.accept(jump, !commit);
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

    /**
     * Top half of a row expands toward the top of the list ({@link Edge#BEFORE}).
     */
    static boolean clickInTopHalf(double contentY, int rowTop, int rowHeight) {
        return contentY < rowTop + rowHeight / 2.0;
    }

    static int highlightLeft(int textX) {
        return textX - HIGHLIGHT_PAD_LEFT;
    }

    static int highlightHeight() {
        return ROW_HEIGHT - HIGHLIGHT_TRIM_BOTTOM;
    }

    private Edge expandSide(int row, double localY) {
        int contentY = (int) Math.round(localY + scrollY - contentOrigin());
        return clickInTopHalf(contentY, layout.rowY(row), layout.rowHeight(row)) ? Edge.BEFORE : Edge.AFTER;
    }

    private int rowAtLocalY(double localY) {
        if (layout.size() == 0) return -1;
        int contentY = (int) Math.round(localY + scrollY - contentOrigin());
        int index = layout.rowAtY(contentY);
        if (index < 0 || index >= window.rows().size()) return -1;
        int top = layout.rowY(index);
        if (contentY < top || contentY >= top + layout.rowHeight(index)) return -1;
        return index;
    }

    private int screenY(int contentY) {
        return y + contentOrigin() + contentY - (int) Math.round(scrollY);
    }

    private int contentOrigin() {
        return MessageListLayout.bottomPad(layout.contentHeight(), height);
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private int listWidth() {
        return Math.max(0, width - TIMELINE_WIDTH);
    }

    private int timestampWidth() {
        return font().width(MessageText.TIMESTAMP_GUTTER);
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
        int yInRow = (int) Math.round(localY + scrollY - contentOrigin() - layout.rowY(row));
        int line = yInRow < 0 ? 0 : yInRow / ROW_HEIGHT;
        DisplayRow displayRow = rows.get(row);
        return MessageWrap.charIndex(displayRow.message(), messageWidth(), line, xInMessage, rangeWidth(displayRow));
    }

    private int messageRangeWidth(DisplayRow row, int from, int to) {
        if (from >= to) return 0;
        return rangeWidth(row).width(from, to);
    }

    /**
     * Per-character prefix widths for one row, measured without obfuscation. Reused across wrap,
     * selection, and hit-testing so obfuscated lines are not rebuilt on every prefix query.
     */
    private MessageWrap.RangeWidth rangeWidth(DisplayRow row) {
        Font font = font();
        if (row == cachedWidthRow && font == cachedWidthFont && cachedRangeWidth != null) {
            return cachedRangeWidth;
        }
        String text = row.message();
        int[] formats = PackedFormatting.perChar(row.visualFormatting(), text.length());
        cachedRangeWidth = MessageWrap.prefixWidths(text.length(), i ->
            font.width(MessageText.measureChar(text.charAt(i), formats[i])));
        cachedWidthRow = row;
        cachedWidthFont = font;
        return cachedRangeWidth;
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

    public record ScrubJump(LocalDateTime time, long skip, double progress) {
    }
}
