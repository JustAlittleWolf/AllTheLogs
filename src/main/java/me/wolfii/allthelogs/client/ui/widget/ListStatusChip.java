package me.wolfii.allthelogs.client.ui.widget;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.ui.text.MessageText;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * The chip in the top-right of the list: a persistent overlay message, a loading label, or the match count
 * and search duration.
 * <p>
 * A fresh count is shown for {@link #VISIBLE_MS} and stays up for as long as the pointer is over the list, so
 * it can be re-read on demand without re-running the search. Loading is only announced after
 * {@link #LOADING_DELAY_MS} so that fast queries do not flash it.
 */
final class ListStatusChip {
    private static final int VISIBLE_MS = 5000;
    private static final int LOADING_DELAY_MS = 100;
    private static final int PAD_X = 8;

    private long matchCount;
    private boolean exactMatchCount;
    private long elapsedMs;
    private boolean showMatchCount;
    private long visibleUntilMs;
    private Component overlay = Component.empty();
    private boolean loading;
    private long loadingSinceMs;

    long matchCount() {
        return matchCount;
    }

    boolean exactMatchCount() {
        return exactMatchCount;
    }

    long elapsedMs() {
        return elapsedMs;
    }

    boolean loading() {
        return loading;
    }

    void setLoading(boolean loading) {
        this.loading = loading;
        this.loadingSinceMs = loading ? System.currentTimeMillis() : 0;
    }

    /**
     * Shows the count of the page that just arrived. Counts above 99 read as {@code >99} until
     * {@link #showTotalMatchCount} supplies the unpaged total.
     */
    void showMatchCount(long matches, long elapsedMs) {
        show(matches, matches <= 99, elapsedMs);
        this.overlay = Component.empty();
    }

    /**
     * Replaces a capped {@code >99} label with the exact total.
     */
    void showTotalMatchCount(long total, long elapsedMs) {
        show(total, true, elapsedMs);
    }

    void showOverlay(Component message) {
        this.overlay = message == null ? Component.empty() : message;
        this.showMatchCount = false;
    }

    /**
     * Whether the loading label has been pending long enough to be worth showing.
     */
    boolean showingLoading() {
        return loading && loadingSinceMs > 0 && System.currentTimeMillis() - loadingSinceMs >= LOADING_DELAY_MS;
    }

    void draw(OwoUIGraphics graphics, ListView view, boolean pointerOverList) {
        boolean persistent = !overlay.getString().isEmpty();
        boolean timed = showMatchCount && (pointerOverList || System.currentTimeMillis() < visibleUntilMs);
        boolean showLoading = showingLoading();
        if (!persistent && !timed && !showLoading) {
            showMatchCount = false;
            return;
        }
        Component text = MessageText.listStatus(overlay, showLoading, timed || showMatchCount, matchCount,
            exactMatchCount, elapsedMs);
        Font font = view.font();
        int listWidth = view.listWidth();
        int boxWidth = Math.min(listWidth - 2 * PAD_X, font.width(text) + 2 * PAD_X);
        int boxHeight = MessageListLayout.DATE_HEIGHT;
        int boxX = view.x() + Math.max(PAD_X, listWidth - PAD_X - boxWidth);
        int boxY = view.y();
        HoverChip.fill(graphics, boxX, boxY, boxWidth, boxHeight, Colors.STATUS_CHIP);
        int textY = boxY + Math.max(0, (boxHeight - font.lineHeight) / 2) + 2;
        graphics.drawText(text, boxX + PAD_X, textY, 1, Colors.TEXT);
    }

    private void show(long matches, boolean exact, long elapsedMs) {
        this.matchCount = Math.max(0, matches);
        this.exactMatchCount = exact;
        this.elapsedMs = Math.max(0, elapsedMs);
        this.showMatchCount = true;
        this.visibleUntilMs = System.currentTimeMillis() + VISIBLE_MS;
    }
}
