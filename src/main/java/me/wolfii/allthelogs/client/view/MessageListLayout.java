package me.wolfii.allthelogs.client.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Vertical layout for the virtualised message list: a sticky date header for each calendar day, a larger gap
 * when two neighbouring rows belong to matches further apart than the configured context, and a height of
 * {@link #ROW_HEIGHT} per wrapped chat line.
 */
public final class MessageListLayout {
    public static final int ROW_HEIGHT = 12;
    public static final int DATE_HEIGHT = 20;
    public static final int DATE_GAP = 6;
    public static final int CLUSTER_GAP = 8;

    private final int[] rowY;
    private final int[] rowHeight;
    private final List<DateBand> dates;
    private final int contentHeight;

    private MessageListLayout(int[] rowY, int[] rowHeight, List<DateBand> dates, int contentHeight) {
        this.rowY = rowY;
        this.rowHeight = rowHeight;
        this.dates = dates;
        this.contentHeight = contentHeight;
    }

    public static MessageListLayout of(List<DisplayRow> rows, int contextLines) {
        return of(rows, contextLines, Integer.MAX_VALUE, text -> text.length());
    }

    public static MessageListLayout of(List<DisplayRow> rows, int contextLines, int messageWidth,
                                       ToIntFunction<String> widthOf) {
        if (rows.isEmpty()) {
            return new MessageListLayout(new int[0], new int[0], List.of(), 0);
        }
        int[] rowY = new int[rows.size()];
        int[] rowHeight = new int[rows.size()];
        List<DateBand> dates = new ArrayList<>();
        int y = 0;
        LocalDate previousDate = null;
        for (int i = 0; i < rows.size(); i++) {
            DisplayRow row = rows.get(i);
            LocalDate date = row.entry().timestamp().toLocalDate();
            if (!date.equals(previousDate)) {
                if (previousDate != null) {
                    y += DATE_GAP;
                }
                dates.add(new DateBand(date, y, i));
                y += DATE_HEIGHT;
                previousDate = date;
            } else if (i > 0 && needsClusterGap(rows.get(i - 1), row, contextLines)) {
                y += CLUSTER_GAP;
            }
            int lines = Math.max(1, MessageWrap.lineCount(row.message(), messageWidth, widthOf));
            rowY[i] = y;
            rowHeight[i] = lines * ROW_HEIGHT;
            y += rowHeight[i];
        }
        return new MessageListLayout(rowY, rowHeight, List.copyOf(dates), y);
    }

    /**
     * Extra lines fetched around a double-clicked row, on top of whatever is already on screen.
     */
    public static int extraContextLines(int contextLines) {
        return Math.min(100, 2 * Math.max(0, contextLines));
    }

    static boolean needsClusterGap(DisplayRow previous, DisplayRow current, int contextLines) {
        if (!previous.chatLog().equals(current.chatLog())) return true;
        int gap = Math.abs(current.lineIndex() - previous.lineIndex());
        return gap > 1 && gap > contextLines;
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

    public DateBand nextDate(DateBand band) {
        if (band == null) return dates.isEmpty() ? null : dates.getFirst();
        int index = dates.indexOf(band);
        if (index < 0 || index + 1 >= dates.size()) return null;
        return dates.get(index + 1);
    }

    public record DateBand(LocalDate date, int y, int firstRow) {
    }
}
