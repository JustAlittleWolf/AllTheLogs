package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.list.*;
import me.wolfii.allthelogs.client.ui.text.MessageText;
import me.wolfii.allthelogs.client.ui.text.ObfuscatedGlyphs;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Draws the message list: only the rows that intersect the viewport, their match highlights and selection,
 * the sticky date headers, and the hover card for the row under the pointer.
 */
final class MessageListPainter {
    private static final int ROW_HEIGHT = MessageListLayout.ROW_HEIGHT;
    private static final float DATE_SCALE = 1.25f;
    private static final int HIGHLIGHT_PAD_LEFT = 1;
    private static final int HIGHLIGHT_TRIM_BOTTOM = 2;
    private static final int INFO_MAX_WIDTH = 160;
    private static final int INFO_PAD = 5;

    private final RowWidths rowWidths;

    MessageListPainter(RowWidths rowWidths) {
        this.rowWidths = rowWidths;
    }

    static int highlightLeft(int textX) {
        return textX - HIGHLIGHT_PAD_LEFT;
    }

    static int highlightHeight() {
        return ROW_HEIGHT - HIGHLIGHT_TRIM_BOTTOM;
    }

    static MessageListLayout.ExpandDirection expandAt(ListView view, double localX, double localY) {
        MessageListLayout.Separator separator = view.layout().separatorAt(view.contentY(localY));
        if (separator == null) return null;
        return MessageListLayout.expandAtLocalX(separator, view.messageLocalX(), localX);
    }

    void drawRows(OwoUIGraphics graphics, ListView view, MessageSelection selection, boolean showingLoading) {
        List<DisplayRow> rows = view.rows();
        int listWidth = view.listWidth();
        graphics.enableScissor(view.x(), view.y(), view.x() + listWidth, view.y() + view.height());
        try {
            if (rows.isEmpty()) {
                if (!showingLoading) {
                    graphics.drawText(Component.translatable("allthelogs.status.empty"),
                        view.x() + 8, view.y() + 8, 1, Colors.MUTED);
                }
                return;
            }
            MessageListLayout layout = view.layout();
            int messageWidth = view.messageWidth();
            int messageX = view.messageX();
            int first = Math.max(0, layout.rowAtY(view.scrollY() - view.contentOrigin()));
            int last = Math.min(rows.size() - 1,
                layout.rowAtY(view.scrollY() - view.contentOrigin() + view.height()) + 1);
            for (int i = first; i <= last; i++) {
                DisplayRow row = rows.get(i);
                int rowY = view.screenY(layout.rowY(i));
                List<MessageWrap.Line> lines = MessageWrap.wrap(row.message(), messageWidth,
                    rowWidths.of(row, view.font()));
                drawHighlights(graphics, view, row, lines, messageX, rowY);
                drawSelection(graphics, view, row, i, selection, lines, messageX, rowY);
                int timestampColor = row.match() ? Colors.MUTED : Colors.CONTEXT_TIMESTAMP;
                graphics.drawText(MessageText.timestamp(row), view.x() + ListView.PAD, rowY + 1, 1, timestampColor);
                int lineY = rowY;
                long tick = ObfuscatedGlyphs.tick();
                for (MessageWrap.Line line : lines) {
                    graphics.drawText(MessageText.messageRange(row, line.start(),
                            line.start() + line.text().length(), view.font(), tick),
                        messageX, lineY + 1, 1, Colors.TEXT);
                    lineY += ROW_HEIGHT;
                }
            }
            drawSeparators(graphics, view);
            drawDateHeaders(graphics, view);
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * Hover card for the row whose timestamp gutter the pointer is over.
     */
    void drawMessageInfo(OwoUIGraphics graphics, ListView view, int mouseX, int mouseY) {
        if (mouseY < view.y() || mouseY >= view.y() + view.height()) return;
        if (mouseX < view.x() || mouseX >= view.x() + view.listWidth()) return;
        if (expandAt(view, mouseX - view.x(), mouseY - view.y()) != null) return;
        if (mouseX < view.x() + ListView.PAD || mouseX >= view.messageX()) return;
        int row = view.rowAt(mouseY - view.y());
        if (row < 0) return;

        Font font = view.font();
        int listWidth = view.listWidth();
        int maxTextWidth = Math.min(INFO_MAX_WIDTH, Math.max(48, listWidth - 16));
        List<Component> lines = MessageText.messageInfo(view.rows().get(row), maxTextWidth,
            text -> (int) Math.ceil(font.width(text) * MessageText.INFO_SCALE));
        drawChip(graphics, view, mouseX, mouseY, lines);
    }

    private void drawChip(OwoUIGraphics graphics, ListView view, int mouseX, int mouseY, List<Component> lines) {
        Font font = view.font();
        int listWidth = view.listWidth();
        int lineHeight = Math.max(8, Math.round(font.lineHeight * MessageText.INFO_SCALE) + 1);
        int textWidth = 0;
        for (Component line : lines) {
            textWidth = Math.max(textWidth, (int) Math.ceil(font.width(line) * MessageText.INFO_SCALE));
        }
        int boxWidth = Math.min(listWidth - 8, textWidth + INFO_PAD * 2);
        int boxHeight = INFO_PAD * 2 + lines.size() * lineHeight - 2;
        int boxX = Math.clamp(mouseX + 8, view.x(), Math.max(view.x(), view.x() + listWidth - boxWidth));
        int boxY = mouseY + 12;
        if (boxY + boxHeight > view.y() + view.height()) {
            boxY = Math.max(view.y(), mouseY - boxHeight - 4);
        }
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, Colors.HOVER_CHIP);
        int textY = boxY + INFO_PAD;
        for (Component line : lines) {
            int color = line.getStyle().getColor() == null
                ? Colors.TEXT
                : (0xFF000000 | line.getStyle().getColor().getValue());
            graphics.drawText(line, boxX + INFO_PAD, textY, MessageText.INFO_SCALE, color);
            textY += lineHeight;
        }
    }

    private void drawSeparators(OwoUIGraphics graphics, ListView view) {
        int listLeft = view.x();
        int listRight = view.x() + view.listWidth();
        int viewTop = view.y();
        int viewBottom = view.y() + view.height();
        for (MessageListLayout.Separator separator : view.layout().separators()) {
            int top = view.screenY(separator.y());
            int bottom = top + MessageListLayout.SEPARATOR_HEIGHT;
            if (bottom < viewTop || top > viewBottom) continue;
            int midY = top + MessageListLayout.SEPARATOR_HEIGHT / 2;
            int caretX = listLeft + view.messageLocalX();
            int caretY = midY - 4;
            int lineLeft = listLeft + ListView.PAD;
            int lineRight = listRight - ListView.PAD;
            int band = MessageListLayout.caretBandWidth(separator);
            int holeLeft = caretX - MessageListLayout.CARET_LINE_INSET;
            int holeRight = caretX + band + MessageListLayout.CARET_LINE_INSET;
            if (band == 0) {
                if (lineRight > lineLeft) {
                    graphics.fill(lineLeft, midY, lineRight, midY + 1, Colors.SEPARATOR);
                }
            } else {
                if (holeLeft > lineLeft) {
                    graphics.fill(lineLeft, midY, holeLeft, midY + 1, Colors.SEPARATOR);
                }
                if (lineRight > holeRight) {
                    graphics.fill(holeRight, midY, lineRight, midY + 1, Colors.SEPARATOR);
                }
            }
            if (separator.expandUp()) {
                graphics.drawText(Component.literal(MessageListLayout.CARET_UP), caretX, caretY, 1,
                    Colors.SEPARATOR_CARET);
                caretX += MessageListLayout.CARET_WIDTH + MessageListLayout.CARET_GAP;
            }
            if (separator.expandDown()) {
                graphics.drawText(Component.literal(MessageListLayout.CARET_DOWN), caretX, caretY, 1,
                    Colors.SEPARATOR_CARET);
            }
        }
    }

    private void drawHighlights(OwoUIGraphics graphics, ListView view, DisplayRow row,
                                List<MessageWrap.Line> lines, int messageX, int rowY) {
        if (!row.match() || row.highlights().isEmpty()) return;
        int lineY = rowY;
        for (MessageWrap.Line line : lines) {
            int lineStart = line.start();
            int lineEnd = lineStart + line.text().length();
            for (HighlightSpan span : row.highlights()) {
                int from = Math.max(span.start(), lineStart);
                int to = Math.min(span.end(), lineEnd);
                if (from >= to) continue;
                int left = highlightLeft(messageX + rowWidths.width(row, view.font(), lineStart, from));
                int right = messageX + rowWidths.width(row, view.font(), lineStart, to);
                graphics.fill(left, lineY, right, lineY + highlightHeight(), Colors.MATCH_HIGHLIGHT);
            }
            lineY += ROW_HEIGHT;
        }
    }

    private void drawSelection(OwoUIGraphics graphics, ListView view, DisplayRow row, int rowIndex,
                               MessageSelection selection, List<MessageWrap.Line> lines, int messageX, int rowY) {
        if (selection.isEmpty()) return;
        int lineY = rowY;
        for (MessageWrap.Line line : lines) {
            String text = line.text();
            int start = -1;
            for (int i = 0; i <= text.length(); i++) {
                boolean covered = i < text.length() && selection.covers(rowIndex, line.start() + i);
                if (covered && start < 0) start = i;
                if (!covered && start >= 0) {
                    int left = messageX + rowWidths.width(row, view.font(), line.start(), line.start() + start);
                    int right = messageX + rowWidths.width(row, view.font(), line.start(), line.start() + i);
                    graphics.fill(left, lineY, right, lineY + ROW_HEIGHT, Colors.SELECTION);
                    start = -1;
                }
            }
            lineY += ROW_HEIGHT;
        }
    }

    /**
     * Date headers in place, plus the header of the scrolled-into day pinned to the top of the list. Pinning
     * is skipped while the content is bottom-aligned, because then no header has scrolled off yet.
     */
    private void drawDateHeaders(OwoUIGraphics graphics, ListView view) {
        MessageListLayout layout = view.layout();
        if (view.contentOrigin() > 0) {
            for (MessageListLayout.DateBand band : layout.dates()) {
                drawBandIfVisible(graphics, view, band.date(), view.screenY(band.y()));
            }
            return;
        }
        MessageListLayout.DateBand sticky = layout.stickyAt(view.scrollY());
        for (MessageListLayout.DateBand band : layout.dates()) {
            if (band == sticky) continue;
            drawBandIfVisible(graphics, view, band.date(), view.screenY(band.y()));
        }
        if (sticky == null) return;
        int headerY = view.y();
        MessageListLayout.DateBand next = layout.nextDate(sticky);
        if (next != null) {
            int nextScreenY = view.screenY(next.y());
            if (nextScreenY < headerY + MessageListLayout.DATE_HEIGHT) {
                headerY = nextScreenY - MessageListLayout.DATE_HEIGHT;
            }
        }
        drawDateBand(graphics, view, sticky.date(), headerY);
    }

    private void drawBandIfVisible(OwoUIGraphics graphics, ListView view, LocalDate date, int headerY) {
        if (headerY + MessageListLayout.DATE_HEIGHT < view.y() || headerY > view.y() + view.height()) return;
        drawDateBand(graphics, view, date, headerY);
    }

    private void drawDateBand(OwoUIGraphics graphics, ListView view, LocalDate date, int headerY) {
        graphics.fill(view.x(), headerY, view.x() + view.listWidth(),
            headerY + MessageListLayout.DATE_HEIGHT, Colors.DATE_BAND);
        graphics.drawText(MessageText.dateHeader(date), view.x() + ListView.PAD,
            headerY + MessageListLayout.DATE_HEIGHT, DATE_SCALE, Colors.TEXT, OwoUIGraphics.TextAnchor.BOTTOM_LEFT);
    }
}
