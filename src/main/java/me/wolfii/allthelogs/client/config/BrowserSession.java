package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;

/**
 * In-memory browser filter for this Minecraft run. Closing the messages screen keeps the last search
 * until the game quits; nothing is written to {@code allthelogs.json}.
 */
public final class BrowserSession {
    private static SearchFilter filter;

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

    static void clear() {
        filter = null;
    }
}
