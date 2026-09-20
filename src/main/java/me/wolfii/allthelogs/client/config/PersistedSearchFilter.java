package me.wolfii.allthelogs.client.config;

import com.google.gson.JsonObject;
import me.wolfii.allthelogs.client.search.SearchFilter;

import java.time.LocalDateTime;

/**
 * Disk representation of the browser filter. Paging, sort, and page size stay at their defaults.
 */
public final class PersistedSearchFilter {
    private PersistedSearchFilter() {
    }

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
