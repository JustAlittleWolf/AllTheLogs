package me.wolfii.allthelogs.client.list;

import java.time.LocalDate;
import java.util.List;

/**
 * Inclusive-start exclusive-end character range of selected message text, spanning one or more displayed rows.
 * Timestamps are never part of the selection.
 */
public final class MessageSelection {
    private boolean empty = true;
    private int startRow;
    private int startChar;
    private int endRow;
    private int endChar;

    public boolean isEmpty() {
        return empty;
    }

    public void clear() {
        empty = true;
        startRow = endRow = startChar = endChar = 0;
    }

    public void start(int row, int character) {
        empty = false;
        startRow = endRow = Math.max(0, row);
        startChar = endChar = Math.max(0, character);
    }

    /**
     * Selects every character of {@code row}. Used for a single click on a message.
     */
    public void selectRow(int row, int length) {
        empty = false;
        startRow = endRow = Math.max(0, row);
        startChar = 0;
        endChar = Math.max(0, length);
    }

    public void extend(int row, int character) {
        if (empty) {
            start(row, character);
            return;
        }
        endRow = Math.max(0, row);
        endChar = Math.max(0, character);
    }

    /**
     * Selects every currently loaded message that falls on {@code date}, from the first character of the
     * first such row through the end of the last.
     */
    public void selectDate(List<DisplayRow> rows, LocalDate date) {
        if (rows == null || rows.isEmpty() || date == null) {
            clear();
            return;
        }
        int first = -1;
        int last = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (date.equals(rows.get(i).entry().timestamp().toLocalDate())) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0) {
            clear();
            return;
        }
        empty = false;
        startRow = first;
        startChar = 0;
        endRow = last;
        endChar = rows.get(last).message().length();
    }

    /**
     * Keeps the selected range when loaded rows are replaced, matching by {@link DisplayRow#key()}.
     * Clears the selection if either end is no longer loaded.
     */
    public void retainIn(List<DisplayRow> previous, List<DisplayRow> next) {
        if (empty) return;
        if (previous == null || next == null || previous.isEmpty() || next.isEmpty()) {
            clear();
            return;
        }
        if (startRow < 0 || startRow >= previous.size() || endRow < 0 || endRow >= previous.size()) {
            clear();
            return;
        }
        DisplayRow.RowKey startKey = previous.get(startRow).key();
        DisplayRow.RowKey endKey = previous.get(endRow).key();
        int newStart = DisplayRows.indexOf(next, startKey);
        int newEnd = DisplayRows.indexOf(next, endKey);
        if (newStart < 0 || newEnd < 0) {
            clear();
            return;
        }
        startRow = newStart;
        endRow = newEnd;
        String startMessage = next.get(startRow).message();
        String endMessage = next.get(endRow).message();
        startChar = Math.clamp(startChar, 0, startMessage.length());
        endChar = Math.clamp(endChar, 0, endMessage.length());
        if (startRow == endRow && startChar == endChar) {
            clear();
        }
    }

    public String copy(List<DisplayRow> rows) {
        if (empty || rows.isEmpty()) return "";
        int fromRow = Math.clamp(Math.min(startRow, endRow), 0, rows.size() - 1);
        int toRow = Math.clamp(Math.max(startRow, endRow), 0, rows.size() - 1);
        int fromChar = startRow <= endRow ? startChar : endChar;
        int toChar = startRow <= endRow ? endChar : startChar;
        if (fromRow == toRow) {
            String message = rows.get(fromRow).message();
            int start = Math.clamp(Math.min(fromChar, toChar), 0, message.length());
            int end = Math.clamp(Math.max(fromChar, toChar), 0, message.length());
            return message.substring(start, end);
        }
        StringBuilder text = new StringBuilder();
        String first = rows.get(fromRow).message();
        text.append(first.substring(Math.clamp(fromChar, 0, first.length())));
        for (int i = fromRow + 1; i < toRow; i++) {
            text.append('\n').append(rows.get(i).message());
        }
        String last = rows.get(toRow).message();
        text.append('\n').append(last.substring(0, Math.clamp(toChar, 0, last.length())));
        return text.toString();
    }

    public boolean covers(int row, int character) {
        if (empty) return false;
        int fromRow = Math.min(startRow, endRow);
        int toRow = Math.max(startRow, endRow);
        if (row < fromRow || row > toRow) return false;
        int fromChar = startRow <= endRow ? startChar : endChar;
        int toChar = startRow <= endRow ? endChar : startChar;
        if (fromRow == toRow) {
            int start = Math.min(fromChar, toChar);
            int end = Math.max(fromChar, toChar);
            return character >= start && character < end;
        }
        if (row == fromRow) return character >= fromChar;
        if (row == toRow) return character < toChar;
        return true;
    }
}
