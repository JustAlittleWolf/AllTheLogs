package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.search.SearchFilter;

/**
 * Restores the log browser filter according to {@link FilterPersistence}.
 */
public final class BrowserFilterMemory {
    private static SearchFilter session;

    private BrowserFilterMemory() {
    }

    public static SearchFilter openingFilter() {
        return openingFilter(AllTheLogsConfig.get());
    }

    public static SearchFilter openingFilter(AllTheLogsConfig config) {
        return switch (config.filterPersistence()) {
            case NOT_PERSISTED -> SearchFilter.defaults();
            case SESSION -> session == null ? SearchFilter.defaults() : session;
            case ACROSS_RESTARTS -> config.persistedFilter();
        };
    }

    public static void remember(SearchFilter filter) {
        remember(filter, AllTheLogsConfig.get(), true);
    }

    public static void remember(SearchFilter filter, AllTheLogsConfig config, boolean writeDisk) {
        SearchFilter value = filter == null ? SearchFilter.defaults() : filter;
        switch (config.filterPersistence()) {
            case NOT_PERSISTED -> session = null;
            case SESSION -> session = value;
            case ACROSS_RESTARTS -> {
                session = value;
                config.setPersistedFilter(value);
                if (writeDisk) config.save();
            }
        }
    }

    static void clearSession() {
        session = null;
    }
}
