package me.wolfii.allthelogs.client.list;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Vertical layout for the virtualised message list: a sticky date header for each calendar day, a separator
 * when neighbouring rows belong to different sessions or when context lines end, and a height of
 * {@link #ROW_HEIGHT} per wrapped chat line.
 */
public final class MessageListLayout {
    public static final int ROW_HEIGHT = 12;
    public static final int DATE_HEIGHT = 16;
    public static final int DATE_GAP = 6;
    /** Space reserved for the gray rule (and optional expand carets) between clusters. */
    public static final int SEPARATOR_HEIGHT = 12;
    /** Extra log lines loaded when an expand caret is clicked. */
    public static final int EXPAND_LINES = 10;
    /** Up-pointing triangle, without a tail. */
    public static final String CARET_UP = "\u25B2";
    /** Down-pointing triangle, without a tail. */
    public static final String CARET_DOWN = "\u25BC";
    public static final int CARET_WIDTH = 8;
    public static final int CARET_GAP = 3;
    /** Extra space so the gray rule does not run through a caret glyph. */
    public static final int CARET_LINE_INSET = 2;

    private final int[] rowY;
    private final int[] rowHeight;
    private final List<DateBand> dates;
    private final List<Separator> separators;
    private final int contentHeight;

    private MessageListLayout(int[] rowY, int[] rowHeight, List<DateBand> dates, List<Separator> separators,
                              int contentHeight) {
        this.rowY = rowY;
        this.rowHeight = rowHeight;
        this.dates = dates;
        this.separators = separators;
        this.contentHeight = contentHeight;
    }

    /**
     * Lays out rows without measuring text, for callers that only need the unwrapped content height.
     */
    public static MessageListLayout of(List<DisplayRow> rows, int contextLines) {
        return of(rows, contextLines, Integer.MAX_VALUE, (row, from, to) -> to - from);
    }

    /**
     * Lays out rows using per-character widths from {@code widthOf}, so bold and other styles that change
     * glyph width wrap the same way they are drawn.
     */
    public static MessageListLayout of(List<DisplayRow> rows, int contextLines, int messageWidth,
                                       RowRangeWidth widthOf) {
        return layout(rows, messageWidth, widthOf);
    }

    private static MessageListLayout layout(List<DisplayRow> rows, int messageWidth, RowRangeWidth widthOf) {
        if (rows.isEmpty()) {
            return new MessageListLayout(new int[0], new int[0], List.of(), List.of(), 0);
        }
        int[] rowY = new int[rows.size()];
        int[] rowHeight = new int[rows.size()];
        List<DateBand> dates = new ArrayList<>();
        List<Separator> separators = new ArrayList<>();
        int y = 0;
        LocalDate previousDate = null;
        for (int i = 0; i < rows.size(); i++) {
            DisplayRow row = rows.get(i);
            LocalDate date = row.entry().timestamp().toLocalDate();
            boolean newDate = !date.equals(previousDate);
            if (newDate) {
                if (previousDate != null) {
                    if (rows.get(i - 1).expandDown()) {
                        separators.add(new Separator(y, i, false, true));
                        y += SEPARATOR_HEIGHT;
                    }
                    y += DATE_GAP;
                }
                dates.add(new DateBand(date, y));
                y += DATE_HEIGHT;
                if (row.expandUp()) {
                    separators.add(new Separator(y, i, true, false));
                    y += SEPARATOR_HEIGHT;
                }
                previousDate = date;
            } else if (i > 0) {
                Separator separator = separatorBetween(y, i, rows.get(i - 1), row);
                if (separator != null) {
                    separators.add(separator);
                    y += SEPARATOR_HEIGHT;
                }
            }
            int lines = Math.max(1, MessageWrap.lineCount(row.message(), messageWidth,
                (from, to) -> widthOf.width(row, from, to)));
            rowY[i] = y;
            rowHeight[i] = lines * ROW_HEIGHT;
            y += rowHeight[i];
        }
        if (rows.getLast().expandDown()) {
            separators.add(new Separator(y, rows.size(), false, true));
            y += SEPARATOR_HEIGHT;
        }
        return new MessageListLayout(rowY, rowHeight, List.copyOf(dates), List.copyOf(separators), y);
    }

    /**
     * Extra lines fetched when an expand caret is clicked.
     */
    public static int extraContextLines() {
        return EXPAND_LINES;
    }

    /**
     * Expanding toward the top of the list loads older messages when the list is oldest-first, and
     * newer ones when it is newest-first.
     */
    public static boolean expandOlderMessages(boolean towardTop, boolean oldestFirst) {
        return towardTop == oldestFirst;
    }

    /**
     * Local-x where the gray rule starts. The rule runs from the left inset across the list, with a
     * gap around the expand carets so it does not sit behind the triangles.
     */
    public static int separatorLineLocalX(Separator separator, int pad) {
        return pad;
    }

    /**
     * Width of the expand caret cluster, or {@code 0} when this rule has no carets.
     */
    public static int caretBandWidth(Separator separator) {
        int count = (separator.expandUp() ? 1 : 0) + (separator.expandDown() ? 1 : 0);
        if (count == 0) return 0;
        return count * CARET_WIDTH + (count - 1) * CARET_GAP;
    }

    /**
     * Which expand caret {@code localX} is over, or {@code null} when the pointer is on the rule itself.
     * {@code originX} is the message-column origin of the carets.
     */
    public static ExpandDirection expandAtLocalX(Separator separator, int pad, double localX) {
        int x = pad;
        if (separator.expandUp()) {
            if (localX >= x && localX < x + CARET_WIDTH) return ExpandDirection.UP;
            x += CARET_WIDTH + CARET_GAP;
        }
        if (separator.expandDown()) {
            if (localX >= x && localX < x + CARET_WIDTH) return ExpandDirection.DOWN;
        }
        return null;
    }

    /**
     * Top padding that bottom-aligns content when it is shorter than the viewport.
     */
    public static int bottomPad(int contentHeight, int viewHeight) {
        if (viewHeight <= 0 || contentHeight <= 0 || contentHeight >= viewHeight) return 0;
        return viewHeight - contentHeight;
    }

    static boolean needsSeparator(DisplayRow previous, DisplayRow current) {
        if (!previous.sameLog(current)) return true;
        return Math.abs(current.lineIndex() - previous.lineIndex()) > 1;
    }

    private static Separator separatorBetween(int y, int afterRow, DisplayRow previous, DisplayRow current) {
        boolean sameLog = previous.sameLog(current);
        if (sameLog && Math.abs(current.lineIndex() - previous.lineIndex()) <= 1) {
            return null;
        }
        if (sameLog && !previous.expandDown() && !current.expandUp()) {
            return null;
        }
        return new Separator(y, afterRow, current.expandUp(), previous.expandDown());
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int rowY(int index) {
        if (index < 0 || index >= rowY.length) return 0;
        return rowY[index];
    }

    public int rowHeight(int index) {
        if (index < 0 || index >= rowHeight.length) return 0;
        return rowHeight[index];
    }

    public int size() {
        return rowY.length;
    }

    public List<DateBand> dates() {
        return dates;
    }

    public List<Separator> separators() {
        return separators;
    }

    /**
     * Separator whose vertical band contains content-y {@code y}, or {@code null}.
     */
    public Separator separatorAt(double y) {
        for (Separator separator : separators) {
            if (y >= separator.y() && y < separator.y() + SEPARATOR_HEIGHT) {
                return separator;
            }
        }
        return null;
    }

    /**
     * Index of the row that contains content-y {@code y}, or {@code -1} when the list is empty.
     */
    public int rowAtY(double y) {
        if (rowY.length == 0) return -1;
        if (y <= rowY[0]) return 0;
        int low = 0;
        int high = rowY.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (rowY[mid] <= y) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Date band that should stick to the top at the given scroll offset, or {@code null} if none.
     */
    public DateBand stickyAt(double scrollY) {
        DateBand current = null;
        for (DateBand band : dates) {
            if (band.y() <= scrollY) {
                current = band;
            } else {
                break;
            }
        }
        return current;
    }

    public DateBand dateBand(LocalDate date) {
        if (date == null) return null;
        for (DateBand band : dates) {
            if (band.date().equals(date)) return band;
        }
        return null;
    }

    /**
     * Content-y just past the last pixel of {@code band}'s rows, which is the next date header or the
     * bottom of the list.
     */
    public int dateEndY(DateBand band) {
        DateBand next = nextDate(band);
        return next == null ? contentHeight : next.y();
    }

    public DateBand nextDate(DateBand band) {
        if (band == null) return dates.isEmpty() ? null : dates.getFirst();
        int index = dates.indexOf(band);
        if (index < 0 || index + 1 >= dates.size()) return null;
        return dates.get(index + 1);
    }

    /**
     * Direction of a separator expand caret relative to the list, not the log file.
     */
    public enum ExpandDirection {
        UP, DOWN
    }

    @FunctionalInterface
    public interface RowRangeWidth {
        int width(DisplayRow row, int from, int to);
    }

    /**
     * A calendar day's sticky header at content-y {@code y}.
     */
    public record DateBand(LocalDate date, int y) {
    }

    /**
     * Gray rule between two displayed clusters. {@code afterRow} is the index of the row below the rule.
     * {@link #expandUp()} loads more context toward the top of the list from that row;
     * {@link #expandDown()} loads more toward the bottom from the row above.
     */
    public record Separator(int y, int afterRow, boolean expandUp, boolean expandDown) {
    }
}
