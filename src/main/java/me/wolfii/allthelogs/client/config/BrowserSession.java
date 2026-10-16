package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;

/**
 * In-memory browser filter for this Minecraft run. Closing the messages screen keeps the last search
 * until the game quits; nothing is written to {@code allthelogs.json}.
 */
public final class BrowserSession {
    private static SearchFilter filter;
    private static int resets;

    private BrowserSession() {
    }

    public static SearchFilter openingFilter() {
        return openingFilter(AllTheLogsConfig.get());
    }

    public static SearchFilter openingFilter(AllTheLogsConfig config) {
        if (filter != null) return filter;
        int context = config == null
            ? SearchFilter.DEFAULT_CONTEXT_LINES
            : config.defaultContextLines();
        return SearchFilter.defaults().withContextLines(context);
    }

    public static void remember(SearchFilter next) {
        filter = next == null ? SearchFilter.defaults() : next;
    }

    /**
     * Starts the messages screen over with default filters, an empty search, and the newest messages. Called
     * after an import or a cleanup changed the stored messages, when the last search and the place in the
     * list may no longer fit them.
     */
    public static void reset() {
        filter = null;
        resets++;
    }

    /**
     * How often {@link #reset()} ran, so a messages screen or a saved place in the list can tell it is stale.
     */
    public static int resets() {
        return resets;
    }

    static void clear() {
        filter = null;
    }
}
