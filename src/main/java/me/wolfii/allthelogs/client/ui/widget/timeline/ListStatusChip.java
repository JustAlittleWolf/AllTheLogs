package me.wolfii.allthelogs.client.ui.widget.timeline;

import me.wolfii.allthelogs.client.ui.text.MessageText;
import net.minecraft.network.chat.Component;

/**
 * Match count and query time drawn at the top-right of the message list: a persistent overlay message,
 * a loading label, or the message/match count.
 * <p>
 * The count stays on screen once a search has produced one. {@code Loading...} appears only after the
 * query has already run for more than a second, without the seconds still ticking. A finished query shows
 * its duration only when it took longer than that.
 */
final class ListStatusChip {

    private long matchCount;
    private boolean exactMatchCount;
    private long elapsedMs;
    private boolean showMatchCount;
    private boolean narrowed;
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
        setLoading(loading, System.currentTimeMillis());
    }

    void setLoading(boolean loading, long nowMs) {
        this.loading = loading;
        this.loadingSinceMs = loading ? nowMs : 0;
    }

    /**
     * Drops a previous search's exact total so the next page can show {@code >99} until the new total arrives.
     */
    void beginNewSearchCount() {
        exactMatchCount = false;
        showMatchCount = false;
    }

    /**
     * Shows the count of the page that just arrived. Counts above 99 read as {@code >99} until
     * {@link #showTotalMatchCount} supplies the unpaged total.
     */
    void showMatchCount(long matches, long elapsedMs, boolean narrowed) {
        this.narrowed = narrowed;
        show(matches, matches <= 99, elapsedMs);
        this.overlay = Component.empty();
    }

    /**
     * Page-sized counts must not replace an exact total. Clearing the search bar keeps the viewport and
     * applies the jump after {@link #showTotalMatchCount}, which would otherwise leave {@code >99} on screen.
     */
    void offerPageMatchCount(long matches, long elapsedMs, boolean narrowed) {
        if (exactMatchCount) return;
        showMatchCount(matches, elapsedMs, narrowed);
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
     * Whether {@code Loading...} is due. Fast queries never reach this, and the label does not include
     * the time still elapsing.
     */
    boolean showingLoading() {
        return showingLoading(System.currentTimeMillis());
    }

    boolean showingLoading(long nowMs) {
        return loading && loadingSinceMs > 0 && nowMs - loadingSinceMs > MessageText.TIMED_STATUS_MS;
    }

    /**
     * Status for {@code nowMs}. The count does not expire. A running query contributes {@code Loading...}
     * only after a second, and never its live elapsed time. A finished query contributes its duration
     * only when that was over a second.
     */
    Component text(long nowMs) {
        return MessageText.listStatus(overlay, showingLoading(nowMs), showMatchCount, matchCount,
            exactMatchCount, elapsedMs, narrowed);
    }

    Component text() {
        return text(System.currentTimeMillis());
    }

    private void show(long matches, boolean exact, long elapsedMs) {
        this.matchCount = Math.max(0, matches);
        this.exactMatchCount = exact;
        this.elapsedMs = Math.max(0, elapsedMs);
        this.showMatchCount = true;
    }
}
