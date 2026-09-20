package me.wolfii.allthelogs.client.config;

import com.google.gson.JsonObject;
import me.wolfii.allthelogs.client.search.SearchFilter;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * How the log browser's search and filter overlay should be restored the next time it opens, plus
 * the session/disk snapshot that mode uses.
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

    private static SearchFilter session;

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

    /**
     * Disk representation of the browser filter. Paging, sort, and page size stay at their defaults.
     */
    public static JsonObject toJson(SearchFilter filter) {
        SearchFilter value = filter == null ? SearchFilter.defaults() : filter;
        JsonObject json = new JsonObject();
        json.addProperty("text", value.text());
        json.addProperty("regex", value.regex());
        json.addProperty("caseSensitive", value.caseSensitive());
        json.addProperty("contextLines", value.contextLines());
        if (value.startingAt() != null) json.addProperty("startingAt", value.startingAt().toString());
        if (value.upUntil() != null) json.addProperty("upUntil", value.upUntil().toString());
        if (value.hasVersion()) json.addProperty("version", value.version());
        if (value.hasServerOrWorld()) json.addProperty("serverOrWorld", value.serverOrWorld());
        return json;
    }

    public static SearchFilter fromJson(JsonObject json) {
        SearchFilter filter = SearchFilter.defaults();
        if (json == null) return filter;
        if (json.has("text")) filter = filter.withText(string(json, "text"));
        if (json.has("regex")) filter = filter.withRegex(json.get("regex").getAsBoolean());
        if (json.has("caseSensitive")) filter = filter.withCaseSensitive(json.get("caseSensitive").getAsBoolean());
        if (json.has("contextLines")) {
            try {
                filter = filter.withContextLines(json.get("contextLines").getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        filter = filter.withStartingAt(dateTime(json, "startingAt"));
        filter = filter.withUpUntil(dateTime(json, "upUntil"));
        if (json.has("version")) filter = filter.withVersion(string(json, "version"));
        if (json.has("serverOrWorld")) filter = filter.withServerOrWorld(string(json, "serverOrWorld"));
        return filter;
    }

    static void clearSession() {
        session = null;
    }

    private static String string(JsonObject json, String key) {
        try {
            return json.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static LocalDateTime dateTime(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return null;
        try {
            String raw = json.get(key).getAsString();
            if (raw == null || raw.isBlank()) return null;
            return LocalDateTime.parse(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
