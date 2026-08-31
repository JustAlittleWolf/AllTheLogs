package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.list.*;
import me.wolfii.allthelogs.client.timeline.ScrubJump;
import me.wolfii.allthelogs.client.timeline.ScrubberGeometry;
import me.wolfii.allthelogs.client.timeline.TimelineEdge;
import me.wolfii.allthelogs.client.timeline.TimelineScale;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.data.MatchDay;
import me.wolfii.allthelogs.data.MatchSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Virtualised log list with a timeline scrubber on its right edge. Newest is at the bottom, and a page shorter
 * than the viewport sits on its bottom edge.
 * <p>
 * Double-click selects a word and triple-click selects the line. Expand carets on cluster
 * separators load more context through {@link #onExpand}; shift-click fetches a larger chunk.
 * its owner to fetch more through {@link #onApproachEdge}, {@link #onJump} and {@link #onExpand} rather than
 * touching the store itself. Drawing lives in {@link MessageListPainter} and {@link TimelineTrackPainter},
 * scrubber state in {@link ScrubDrag}, and the status chip in {@link ListStatusChip}.
 * <p>
 * The scrubber spans the whole matched range from {@link #setMatchSummary}, not just the loaded page, so its
 * thumb is sized from how many days the query matches and dragging it can land outside the buffer.
 */
public final class MessageTimeline extends BaseUIComponent {
    public static final int TIMELINE_WIDTH = 68;
    /** Matches fetched per preview query while the thumb is being dragged. */
    public static final int SCRUB_PAGE_SIZE = 32;
    private static final int ROW_HEIGHT = MessageListLayout.ROW_HEIGHT;
    private static final int SELECT_DRAG_SLOP = 3;
    private static final int MULTI_CLICK_MS = 400;
    private static final int MULTI_CLICK_SLOP = 4;
    /** Rows from either end of the buffer at which the next page is requested. */
    private static final int EDGE_ROWS = 3;

    private final ResultWindow window = new ResultWindow();
    private final MessageSelection selection = new MessageSelection();
    private final RowWidths rowWidths = new RowWidths();
    private final MessageListPainter listPainter = new MessageListPainter(rowWidths);
    private final ListStatusChip status = new ListStatusChip();
    private final ScrubDrag scrub = new ScrubDrag();
    private final AutoScroller autoScroll = new AutoScroller();

    private MessageListLayout layout = MessageListLayout.of(List.of(), 0);
    private MatchSummary matches = MatchSummary.empty();
    private int contextLines;
    private double scrollY;
    private int laidOutWidth = -1;

    private boolean draggingSelection;
    private boolean pendingClear;
    private int clickCount;
    private long lastClickTime;
    private int clickRow = -1;
    private DisplayRow.RowKey clickKey;
    private int clickChar;
    private double clickX;
    private double clickY;

    private Consumer<TimelineEdge> onApproachEdge = edge -> {
    };
    private BiConsumer<ScrubJump, Boolean> onJump = (jump, preview) -> {
    };
    private ExpandHandler onExpand = (row, side, extraLines) -> {
    };
    private Runnable onScrubBegin = () -> {
    };

    public MessageTimeline() {
        this.sizing(Sizing.fill(), Sizing.fill());
        this.cursorStyle(CursorStyle.POINTER);
    }

    private static boolean middleMousePressed() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return false;
        return GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /**
     * Expanding toward the top of the list uses {@link TimelineEdge#BEFORE}.
     */
    public ResultWindow window() {
        return window;
    }

    public void onApproachEdge(Consumer<TimelineEdge> onApproachEdge) {
        this.onApproachEdge = onApproachEdge;
    }

    public void onJump(BiConsumer<ScrubJump, Boolean> onJump) {
        this.onJump = onJump;
    }

    public void onExpand(ExpandHandler onExpand) {
        this.onExpand = onExpand;
    }

    public void onScrubBegin(Runnable onScrubBegin) {
        this.onScrubBegin = onScrubBegin;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
        rebuildLayout();
    }

    /**
     * Sets the matched range the scrubber spans. {@code null} falls back to the loaded page's own range.
     */
    public void setMatchSummary(MatchSummary summary) {
        this.matches = summary == null ? MatchSummary.empty() : summary;
    }

    public void setLoading(boolean loading) {
        status.setLoading(loading);
    }

    public boolean loading() {
        return status.loading();
    }

    public void scrubQueryFinished() {
        scrub.previewFinished();
    }

    public double scrollY() {
        return scrollY;
    }

    public void setScrollY(double scrollY) {
        this.scrollY = clampScroll(scrollY);
    }

    public int viewHeight() {
        return height;
    }

    public int firstVisibleIndex() {
        return view().firstVisibleRow();
    }

    public int lastVisibleIndex() {
        return view().lastVisibleRow();
    }

    public DisplayRow.RowKey visibleAnchor() {
        return window.keyAt(firstVisibleIndex());
    }

    public long matchCount() {
        return status.matchCount();
    }

    public boolean exactMatchCount() {
        return status.exactMatchCount();
    }

    public long matchElapsedMs() {
        return status.elapsedMs();
    }

    /**
     * Shows {@code N message(s)} or {@code N match(es)} over the list for a moment after a search.
     * Counts above 99 read as {@code >99} until {@link #setTotalMatchCount} supplies the exact total.
     */
    public void showMatchCount(long matches, long elapsedMs, boolean narrowed) {
        status.showMatchCount(matches, elapsedMs, narrowed);
    }

    public void setTotalMatchCount(long total) {
        setTotalMatchCount(total, status.elapsedMs());
    }

    public void setTotalMatchCount(long total, long elapsedMs) {
        status.showTotalMatchCount(total, elapsedMs);
    }

    public void showOverlay(Component message) {
        status.showOverlay(message);
    }

    /**
     * Replaces the buffered page with a fresh search result, scrolled to the top.
     */
    public void reset(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        window.reset(rows, hasBefore, hasAfter);
        rebuildLayout();
        this.scrollY = 0;
        selection.clear();
        clearClick();
        finishScrub();
    }

    /**
     * Re-applies a previously loaded page after the widget is rebuilt (resize or focus), keeping scroll.
     */
    public void restore(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, double scrollY) {
        replacePage(rows, hasBefore, hasAfter);
        this.scrollY = clampScroll(scrollY);
        finishScrub();
    }

    /**
     * Replaces the buffered page and keeps {@code anchor} at the same screen position, for paging at either
     * edge of the buffer.
     */
    public void applyPage(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, DisplayRow.RowKey anchor) {
        int oldIndex = DisplayRows.indexOf(window.rows(), anchor);
        double oldY = layout.rowY(oldIndex);
        int oldOrigin = contentOrigin();
        replacePage(rows, hasBefore, hasAfter);
        int newIndex = DisplayRows.indexOf(window.rows(), anchor);
        setScrollY(ResultWindow.keepAnchor(oldIndex, newIndex, oldY, layout.rowY(newIndex), scrollY,
            oldOrigin, contentOrigin()));
        maybeRequestMore();
    }

    /**
     * Replaces the buffered page and scrolls to {@code progress} along the track, or to {@code time} when
     * {@code progress} is {@link Double#NaN}. Used while dragging the timeline, so the list never flashes
     * back to offset 0 first and the thumb can stay put.
     */
    public void showAt(LocalDateTime time, List<DisplayRow> rows, boolean hasBefore, boolean hasAfter,
                       double progress) {
        replacePage(rows, hasBefore, hasAfter);
        if (Double.isNaN(progress)) {
            scrollToTime(time);
        } else {
            scrollToScrubProgress(progress);
        }
        if (!scrub.dragging()) maybeRequestMore();
    }

    public void scrollToEnd() {
        setScrollY(Math.max(0, layout.contentHeight() - height));
    }

    public void scrollToTime(LocalDateTime time) {
        int index = window.nearestIndex(time);
        if (index < 0) return;
        int header = contentOrigin() > 0 ? 0 : MessageListLayout.DATE_HEIGHT;
        setScrollY(ScrubberGeometry.scrollToRow(layout.rowY(index) - header, layout.contentHeight(), height));
    }

    /**
     * Releases the thumb back to following the viewport. Ignored while it is still held.
     */
    public void finishScrub() {
        scrub.finish();
    }

    @Override
    public boolean canFocus(UIComponent.FocusSource source) {
        return true;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int listWidth = ListView.listWidth(width);
        if (listWidth != laidOutWidth) {
            laidOutWidth = listWidth;
            rebuildLayout();
        }
        graphics.fill(x, y, x + listWidth, y + height, Colors.LIST_BACKGROUND);
        if (scrub.dragging()) {
            continueScrub();
        }
        applyAutoScroll(mouseY, delta);

        ListView view = view();
        listPainter.drawRows(graphics, view, selection, status.showingLoading());
        if (!draggingSelection && !scrub.dragging() && !autoScroll.active()) {
            listPainter.drawMessageInfo(graphics, view, mouseX, mouseY);
        }
        status.draw(graphics, view, view.containsScreen(mouseX, mouseY));
        TimelineTrackPainter.draw(graphics, view, track(), mouseX, mouseY, this::timeAtLocalY);
        updateCursor(view, mouseX, mouseY);
    }

    /**
     * Mouse events are component-local (0,0 is this list's top-left). Draw calls still use
     * screen coordinates via {@code x}/{@code y}.
     */
    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (overTimeline(mouseX)) return false;
        if (autoScroll.active()) {
            autoScroll.stop();
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
        if (overTimeline(click.x())) {
            beginScrub(click.y());
            return true;
        }
        if (autoScroll.active()) {
            autoScroll.stop();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            autoScroll.start(click.y());
            draggingSelection = false;
            pendingClear = false;
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            selection.clear();
            pendingClear = false;
            draggingSelection = false;
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.onMouseDown(click, doubled);
        }
        MessageListLayout.ExpandDirection expand = MessageListPainter.expandAt(view(), click.x(), click.y());
        if (expand != null) {
            pendingClear = false;
            draggingSelection = false;
            expandFromSeparator(expand, click.y(), click.hasShiftDown());
            return true;
        }
        int row = view().rowAt(click.y());
        if (row < 0) {
            pendingClear = false;
            draggingSelection = false;
            return super.onMouseDown(click, doubled);
        }
        int clicks = updateClickCount(click.x(), click.y());
        clickRow = row;
        clickKey = window.rows().get(row).key();
        clickChar = charAt(row, click.x(), click.y());
        clickX = click.x();
        clickY = click.y();
        draggingSelection = false;
        if (clicks == 2) {
            pendingClear = false;
            selection.selectWord(row, window.rows().get(row).message(), clickChar);
            return true;
        }
        if (clicks >= 3) {
            pendingClear = false;
            clickCount = 0;
            selection.selectRow(row, window.rows().get(row).message().length());
            return true;
        }
        pendingClear = true;
        return true;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (scrub.dragging()) {
            applyThumbScrub(click.y(), false);
            return true;
        }
        if (autoScroll.active()) {
            return true;
        }
        if (pendingClear && movedPastSelectSlop(click.x(), click.y())) {
            pendingClear = false;
            draggingSelection = true;
            selection.start(clickRow, clickChar);
        }
        if (draggingSelection) {
            int row = view().rowAt(click.y());
            if (row >= 0) selection.extend(row, charAt(row, click.x(), click.y()));
            return true;
        }
        return super.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (scrub.dragging()) {
            scrub.endDrag();
            applyThumbScrub(click.y(), true);
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && pendingClear) {
            selection.clear();
        }
        draggingSelection = false;
        pendingClear = false;
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

    /**
     * Selects every loaded message on the day whose header is currently at the top of the list.
     */
    public boolean selectAllOnVisibleDate() {
        List<DisplayRow> rows = window.rows();
        if (rows.isEmpty()) return false;
        LocalDate date = visibleDate();
        if (date == null) return false;
        selection.selectDate(rows, date);
        return true;
    }

    private ListView view() {
        return new ListView(x, y, width, height, scrollY, layout, window.rows(), font());
    }

    private TimelineTrackPainter.Track track() {
        int thumbHeight = thumbHeight();
        return new TimelineTrackPainter.Track(scrubOldest(), scrubNewest(), matches.days(),
            thumbHeight, thumbTop(thumbHeight), scrub.dragging());
    }

    private void replacePage(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter) {
        List<DisplayRow> previous = window.rows();
        window.reset(rows, hasBefore, hasAfter);
        remapSelection(previous, window.rows());
        rebuildLayout();
    }

    private void remapSelection(List<DisplayRow> previous, List<DisplayRow> next) {
        selection.retainIn(previous, next);
        if (clickKey == null) return;
        int index = DisplayRows.indexOf(next, clickKey);
        if (index < 0) {
            clearClick();
            return;
        }
        clickRow = index;
    }

    private void clearClick() {
        pendingClear = false;
        draggingSelection = false;
        clickRow = -1;
        clickKey = null;
    }

    private void rebuildLayout() {
        layout = MessageListLayout.of(window.rows(), contextLines, ListView.messageWidth(width, font()),
            (row, from, to) -> rowWidths.width(row, font(), from, to));
        this.scrollY = clampScroll(scrollY);
    }

    private void updateCursor(ListView view, int mouseX, int mouseY) {
        boolean inRows = mouseY >= y && mouseY < y + height;
        boolean nearTrack = mouseX >= x + view.listWidth() - TimelineTrackPainter.HOVER_SLOP
            && mouseX < x + width && inRows;
        boolean overMessage = mouseX >= view.messageX() && mouseX < x + view.listWidth() && inRows;
        boolean overExpand = MessageListPainter.expandAt(view, mouseX - x, mouseY - y) != null;
        if (nearTrack || scrub.dragging() || autoScroll.active()) {
            this.cursorStyle(CursorStyle.MOVE);
        } else if (overExpand) {
            this.cursorStyle(CursorStyle.HAND);
        } else if (overMessage) {
            this.cursorStyle(CursorStyle.TEXT);
        } else {
            this.cursorStyle(CursorStyle.POINTER);
        }
    }

    private void applyAutoScroll(int mouseY, float delta) {
        double offset = autoScroll.scrollDelta(mouseY - y, delta, MessageTimeline::middleMousePressed);
        if (offset == 0) return;
        setScrollY(scrollY + offset);
        maybeRequestMore();
    }

    private int updateClickCount(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        double dx = mouseX - clickX;
        double dy = mouseY - clickY;
        boolean nearby = dx * dx + dy * dy <= MULTI_CLICK_SLOP * MULTI_CLICK_SLOP;
        if (nearby && now - lastClickTime <= MULTI_CLICK_MS) {
            clickCount++;
        } else {
            clickCount = 1;
        }
        lastClickTime = now;
        return clickCount;
    }

    private void expandFromSeparator(MessageListLayout.ExpandDirection direction, double localY, boolean shift) {
        MessageListLayout.Separator separator = layout.separatorAt(view().contentY(localY));
        if (separator == null) return;
        List<DisplayRow> rows = window.rows();
        int extra = MessageListLayout.extraContextLines(shift);
        if (direction == MessageListLayout.ExpandDirection.UP) {
            if (separator.afterRow() >= 0 && separator.afterRow() < rows.size()) {
                onExpand.expand(rows.get(separator.afterRow()), TimelineEdge.BEFORE, extra);
            }
            return;
        }
        int previous = separator.afterRow() - 1;
        if (previous >= 0 && previous < rows.size()) {
            onExpand.expand(rows.get(previous), TimelineEdge.AFTER, extra);
        }
    }

    private boolean movedPastSelectSlop(double mouseX, double mouseY) {
        double dx = mouseX - clickX;
        double dy = mouseY - clickY;
        return dx * dx + dy * dy >= SELECT_DRAG_SLOP * SELECT_DRAG_SLOP;
    }

    /**
     * Timestamp of the row at the top of the viewport, which is what the thumb tracks.
     */
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

    private LocalDateTime scrubOldest() {
        return matches.oldest() != null ? matches.oldest() : window.firstMatchTime();
    }

    private LocalDateTime scrubNewest() {
        return matches.newest() != null ? matches.newest() : window.lastMatchTime();
    }

    private LocalDateTime timeAtLocalY(double localY) {
        return timeAtProgress(localY / Math.max(1, height - 1));
    }

    private LocalDateTime timeAtProgress(double progress) {
        if (!matches.days().isEmpty()) {
            return TimelineScale.timeAtProgress(progress, matches.days());
        }
        LocalDateTime oldest = scrubOldest();
        LocalDateTime newest = scrubNewest();
        if (oldest == null || newest == null) return null;
        return TimelineScale.timeAtLinearProgress(progress, oldest, newest);
    }

    private int thumbHeight() {
        if (scrub.dragging() && scrub.capturedThumbHeight() > 0) return scrub.capturedThumbHeight();
        if (window.rows().isEmpty() || height <= 0) return 0;
        return ScrubberGeometry.thumbHeightForDays(height, matches.uniqueDates(), layout.contentHeight(), height);
    }

    private int thumbTop(int thumbHeight) {
        if (scrub.holdsPosition()) {
            return y + scrub.heldThumbTopOffset(height, thumbHeight);
        }
        LocalDateTime time = visibleTime();
        if (time == null || scrubOldest() == null || scrubNewest() == null) return y;
        double progress = ScrubberGeometry.pinnedProgress(thumbProgress(time), scrolledToStart(), scrolledToEnd());
        return y + ScrubberGeometry.thumbOffset(height, progress, thumbHeight);
    }

    private double thumbProgress(LocalDateTime time) {
        if (!matches.days().isEmpty()) {
            return TimelineScale.matchDayProgress(time, matches.days(), collapsedFraction(time.toLocalDate()));
        }
        return TimelineScale.linearProgress(time, scrubOldest(), scrubNewest());
    }

    /**
     * How far the viewport has walked through a day whose matches all share one timestamp, so the thumb still
     * moves as that day is scrolled.
     */
    private double collapsedFraction(LocalDate date) {
        MessageListLayout.DateBand band = layout.dateBand(date);
        if (band == null) return 0;
        int travel = Math.max(0, layout.dateEndY(band) - band.y() - Math.max(0, height));
        if (travel <= 0) {
            double max = Math.max(0, layout.contentHeight() - height);
            return max <= 0 ? 0 : Math.clamp(scrollY / max, 0, 1);
        }
        return Math.clamp((scrollY - band.y()) / (double) travel, 0, 1);
    }

    private boolean scrolledToStart() {
        return !window.hasBefore() && scrollY <= 0.5 && layout.contentHeight() > height;
    }

    private boolean scrolledToEnd() {
        if (window.hasAfter()) return false;
        return scrollY >= Math.max(0, layout.contentHeight() - height) - 0.5;
    }

    private void beginScrub(double localY) {
        autoScroll.stop();
        scrub.begin(thumbHeight());
        if (scrub.capturedThumbHeight() > 0) {
            scrub.grab(localY, thumbTop(scrub.capturedThumbHeight()) - y, height);
        }
        onScrubBegin.run();
        applyThumbScrub(localY, false);
    }

    private void applyThumbScrub(double localY, boolean commit) {
        int thumbHeight = scrub.dragging() && scrub.capturedThumbHeight() > 0
            ? scrub.capturedThumbHeight()
            : thumbHeight();
        applyScrub(scrub.moveTo(localY, thumbHeight, height), commit);
    }

    /**
     * Keeps a parked thumb pulling in pages while it is held still.
     */
    private void continueScrub() {
        double progress = scrub.parkedProgress(height);
        if (!Double.isNaN(progress)) applyScrub(progress, false);
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
        long skip = matches.days().isEmpty() ? -1 : TimelineScale.skipAtProgress(clamped, matches.days());
        if (scrollLocally(clamped, time, skip)) {
            if (!scrub.dragging()) maybeRequestMore();
            if (commit) finishScrub();
            return;
        }
        jump(new ScrubJump(time, skip, clamped), commit);
    }

    private long skipAtEnd() {
        if (matches.days().isEmpty()) return -1;
        return TimelineScale.skipAtProgress(1, matches.days());
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
        LocalDateTime time = timeAtProgress(clamped);
        if (!scrollLocally(clamped, time, -1, true)) {
            scrollToTime(time);
        }
    }

    /**
     * Scrolls to {@code progress} without a store query when the buffer already holds that day. A day whose
     * matches all share one timestamp cannot be reached this way once it holds more matches than are loaded,
     * because only a match rank can address them. A preview slice of a longer day is not enough either:
     * mapping the whole day onto those rows would hide messages the thumb still points at.
     */
    private boolean scrollLocally(double progress, LocalDateTime time, long skip) {
        return scrollLocally(progress, time, skip, false);
    }

    private boolean scrollLocally(double progress, LocalDateTime time, long skip, boolean onFetchedPage) {
        MatchDay day = TimelineScale.dayAtProgress(progress, matches.days());
        if (day != null) {
            MessageListLayout.DateBand band = layout.dateBand(day.date());
            int loaded = DisplayRows.matchCountOnDate(window.rows(), day.date());
            boolean timeInBuffer = time != null && window.coversTime(time) && window.showsDate(time);
            if (band != null && PageBounds.canScrollDayLocally(day, loaded, skip, onFetchedPage, timeInBuffer)) {
                double fraction = TimelineScale.fractionInDay(progress, matches.days());
                setScrollY(ScrubberGeometry.scrollForDateFraction(band.y(), layout.dateEndY(band), height, fraction));
                return true;
            }
            if (day.collapsed() && skip >= 0 && loaded < day.matches()) {
                return false;
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
        if (commit) {
            scrub.markCommitted(jump);
        } else if (!scrub.claimPreview(jump)) {
            return;
        }
        onJump.accept(jump, !commit);
    }

    private void maybeRequestMore() {
        if (status.loading() || scrub.dragging() || window.rows().isEmpty()) return;
        if (window.hasBefore() && firstVisibleIndex() <= EDGE_ROWS - 1) {
            onApproachEdge.accept(TimelineEdge.BEFORE);
        } else if (window.hasAfter() && lastVisibleIndex() >= window.rows().size() - EDGE_ROWS) {
            onApproachEdge.accept(TimelineEdge.AFTER);
        }
    }

    private int charAt(int row, double localX, double localY) {
        List<DisplayRow> rows = window.rows();
        if (row < 0 || row >= rows.size()) return 0;
        ListView view = view();
        int xInMessage = (int) Math.round(localX - view.messageLocalX());
        int yInRow = (int) Math.round(localY + scrollY - contentOrigin() - layout.rowY(row));
        int line = yInRow < 0 ? 0 : yInRow / ROW_HEIGHT;
        DisplayRow displayRow = rows.get(row);
        MessageWrap.RangeWidth widths = rowWidths.of(displayRow, view.font());
        return MessageWrap.charIndex(displayRow.message(), view.messageWidth(), line, xInMessage, widths);
    }

    private boolean overTimeline(double localX) {
        return localX >= width - TIMELINE_WIDTH;
    }

    private int contentOrigin() {
        return MessageListLayout.bottomPad(layout.contentHeight(), height);
    }

    private double clampScroll(double value) {
        double max = Math.max(0, layout.contentHeight() - height);
        if (value < 0) return 0;
        if (value > max) return max;
        return value;
    }

    /**
     * Loads more context from a separator caret. {@code extraLines} is how many neighbouring
     * log lines to fetch on that side.
     */
    @FunctionalInterface
    public interface ExpandHandler {
        void expand(DisplayRow row, TimelineEdge side, int extraLines);
    }
}
