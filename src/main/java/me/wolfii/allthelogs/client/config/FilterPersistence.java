package me.wolfii.allthelogs.client.config;

import java.util.Locale;

/**
 * How the log browser's search and filter overlay should be restored the next time it opens.
 */
public enum FilterPersistence {
    /**
     * Opening the browser always starts at the latest messages with an empty search.
     */
    NOT_PERSISTED,
    /**
     * Remember the filter for this Minecraft run only.
     */
    SESSION,
    /**
     * Write the filter to disk so it survives restarts.
     */
    ACROSS_RESTARTS;

    public FilterPersistence next() {
        FilterPersistence[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static FilterPersistence fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return NOT_PERSISTED;
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return NOT_PERSISTED;
        }
    }
}
