package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.ui.widget.MessageTimeline;

import java.util.List;

/**
 * The last page applied to the widget, so it survives the widget being rebuilt on resize or reopen.
 */
record ListSnapshot(List<DisplayRow> rows, boolean hasBefore, boolean hasAfter, double scrollY,
                    long matchCount, boolean exactMatchCount, long elapsedMs) {
    static final ListSnapshot EMPTY = new ListSnapshot(List.of(), false, false, 0, 0, false, 0);

    static ListSnapshot of(MessageTimeline list) {
        return new ListSnapshot(list.window().rows(), list.window().hasBefore(), list.window().hasAfter(),
            list.scrollY(), list.matchCount(), list.exactMatchCount(), list.matchElapsedMs());
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }
}
