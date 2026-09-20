package me.wolfii.allthelogs.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.search.SearchFilter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * User settings stored as {@code config/allthelogs.json}. The running instance directory is displayed in the
 * settings screen but is never written here.
 */
public final class AllTheLogsConfig {
    public static final String FILE_NAME = "allthelogs.json";
    public static final int DEFAULT_MESSAGE_FONT_SIZE = MessageListLayout.ROW_HEIGHT;
    public static final int MIN_MESSAGE_FONT_SIZE = 6;
    public static final int MAX_MESSAGE_FONT_SIZE = 24;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static AllTheLogsConfig instance;

    private final Path file;
    private List<String> extraImportDirectories = List.of();
    private FilterPersistence filterPersistence = FilterPersistence.NOT_PERSISTED;
    private boolean hideImportButton;
    private int messageFontSize = DEFAULT_MESSAGE_FONT_SIZE;
    private SearchFilter persistedFilter = SearchFilter.defaults();

    AllTheLogsConfig(Path file) {
        this.file = file;
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static AllTheLogsConfig get() {
        if (instance == null) instance = load(defaultPath());
        return instance;
    }

    /**
     * Loads settings from {@code file}, or defaults when the file is missing or unreadable.
     */
    public static AllTheLogsConfig load(Path file) {
        AllTheLogsConfig config = new AllTheLogsConfig(file);
        if (file == null || !Files.isRegularFile(file)) return config;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) config.read(parsed.getAsJsonObject());
        } catch (RuntimeException | IOException error) {
            AllTheLogsClient.LOGGER.warn("Could not read {}", file, error);
        }
        return config;
    }

    public static void loadDefault() {
        instance = load(defaultPath());
    }

    public Path file() {
        return file;
    }

    public List<String> extraImportDirectories() {
        return extraImportDirectories;
    }

    public void setExtraImportDirectories(List<String> directories, Path instanceDir) {
        extraImportDirectories = ExtraImportDirectories.persisted(directories, instanceDir);
    }

    public FilterPersistence filterPersistence() {
        return filterPersistence;
    }

    public void setFilterPersistence(FilterPersistence filterPersistence) {
        this.filterPersistence = filterPersistence == null ? FilterPersistence.NOT_PERSISTED : filterPersistence;
    }

    public boolean hideImportButton() {
        return hideImportButton;
    }

    public void setHideImportButton(boolean hideImportButton) {
        this.hideImportButton = hideImportButton;
    }

    public int messageFontSize() {
        return messageFontSize;
    }

    public void setMessageFontSize(int messageFontSize) {
        this.messageFontSize = clampFontSize(messageFontSize);
    }

    public SearchFilter persistedFilter() {
        return persistedFilter;
    }

    public void setPersistedFilter(SearchFilter filter) {
        this.persistedFilter = filter == null ? SearchFilter.defaults() : filter;
    }

    public static int clampFontSize(int fontSize) {
        return Math.clamp(fontSize, MIN_MESSAGE_FONT_SIZE, MAX_MESSAGE_FONT_SIZE);
    }

    public void save() {
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(toJson(), writer);
            }
        } catch (IOException error) {
            AllTheLogsClient.LOGGER.warn("Could not write {}", file, error);
        }
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonArray extras = new JsonArray();
        for (String directory : extraImportDirectories) extras.add(directory);
        json.add("extraImportDirectories", extras);
        json.addProperty("filterPersistence", filterPersistence.name());
        json.addProperty("hideImportButton", hideImportButton);
        json.addProperty("messageFontSize", messageFontSize);
        json.add("filter", PersistedSearchFilter.toJson(persistedFilter));
        return json;
    }

    private void read(JsonObject json) {
        extraImportDirectories = ExtraImportDirectories.persisted(strings(json, "extraImportDirectories"), null);
        filterPersistence = FilterPersistence.fromConfig(string(json, "filterPersistence"));
        hideImportButton = bool(json, "hideImportButton", false);
        messageFontSize = clampFontSize(integer(json, "messageFontSize", DEFAULT_MESSAGE_FONT_SIZE));
        if (json.has("filter") && json.get("filter").isJsonObject()) {
            persistedFilter = PersistedSearchFilter.fromJson(json.getAsJsonObject("filter"));
        }
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            try {
                values.add(element.getAsString());
            } catch (RuntimeException ignored) {
            }
        }
        return values;
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) return "";
        try {
            return json.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        try {
            return json.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
