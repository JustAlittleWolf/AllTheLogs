package me.wolfii.allthelogs.client.view;

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

    public void extend(int row, int character) {
        if (empty) {
            start(row, character);
            return;
        }
        endRow = Math.max(0, row);
        endChar = Math.max(0, character);
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
