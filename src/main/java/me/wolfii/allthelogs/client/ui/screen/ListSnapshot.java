package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;

import java.util.List;

/**
 * The last page applied to the widget, so it survives the widget being rebuilt on resize or reopen.
 * <p>
 * {@code pinnedToEnd} is set when that page was showing the latest row. Reopening then follows the tail
 * instead of replaying {@code scrollY}, which would sit above messages captured while the screen was closed.
 */
record ListSnapshot(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, double scrollY,
                    boolean pinnedToEnd, long matchCount, boolean exactMatchCount, long elapsedMs) {
    static final ListSnapshot EMPTY = new ListSnapshot(List.of(), false, false, 0, false, 0, false, 0);

    static ListSnapshot of(MessageTimeline list) {
        return new ListSnapshot(list.window().rows(), list.window().hasBefore(), list.window().hasAfter(),
            list.scrollY(), list.pinnedToEnd(), list.matchCount(), list.exactMatchCount(), list.matchElapsedMs());
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }
}
