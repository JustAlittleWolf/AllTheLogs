package me.wolfii.allthelogs.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.ConfigSerializer;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.EnumCycler;
import dev.isxander.yacl3.config.v2.api.autogen.IntSlider;
import dev.isxander.yacl3.config.v2.api.autogen.ListGroup;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.search.SearchFilter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User settings stored as {@code config/allthelogs.json}. Extra import folders, browser chrome, and filter
 * persistence are edited through a YACL autogen screen. The current instance directory is always scanned
 * and is never written here.
 */
public class AllTheLogsConfig {
    public static final String FILE_NAME = "allthelogs.json";
    public static final int DEFAULT_MESSAGE_FONT_SIZE = MessageListLayout.ROW_HEIGHT;
    public static final int MIN_MESSAGE_FONT_SIZE = 6;
    public static final int MAX_MESSAGE_FONT_SIZE = 24;

    private static ConfigClassHandler<AllTheLogsConfig> handler;

    private transient Path file;

    @AutoGen(category = "import")
    @ListGroup(valueFactory = StringListFactory.class, controllerFactory = StringListFactory.class)
    @SerialEntry
    public List<String> extraImportDirectories = new ArrayList<>();

    @AutoGen(category = "browser")
    @EnumCycler
    @SerialEntry
    public FilterPersistence filterPersistence = FilterPersistence.NOT_PERSISTED;

    @AutoGen(category = "browser")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    @SerialEntry
    public boolean hideImportButton;

    @AutoGen(category = "browser")
    @IntSlider(min = MIN_MESSAGE_FONT_SIZE, max = MAX_MESSAGE_FONT_SIZE, step = 1)
    @SerialEntry
    public int messageFontSize = DEFAULT_MESSAGE_FONT_SIZE;

    /**
     * Last browser filter, written only when {@link FilterPersistence#ACROSS_RESTARTS} is selected.
     */
    @SerialEntry
    public SearchFilter filter = SearchFilter.defaults();

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static AllTheLogsConfig get() {
        return handler().instance();
    }

    /**
     * Loads settings from {@code file}, or defaults when the file is missing or unreadable.
     */
    public static AllTheLogsConfig load(Path file) {
        AllTheLogsConfig config = new AllTheLogsConfig();
        config.file = file;
        if (file == null || !Files.isRegularFile(file)) {
            config.normalize();
            return config;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            AllTheLogsConfig loaded = gson().fromJson(reader, AllTheLogsConfig.class);
            if (loaded != null) {
                loaded.file = file;
                loaded.normalize();
                return loaded;
            }
        } catch (RuntimeException | IOException error) {
            AllTheLogsClient.LOGGER.warn("Could not read {}", file, error);
        }
        config.normalize();
        return config;
    }

    public static void loadDefault() {
        handler().load();
        get().normalize();
    }

    public static Screen createScreen(Screen parent) {
        return handler().generateGui().generateScreen(parent);
    }

    public Path file() {
        return file;
    }

    public List<String> extraImportDirectories() {
        return extraImportDirectories == null ? List.of() : List.copyOf(extraImportDirectories);
    }

    public void setExtraImportDirectories(List<String> directories, Path instanceDir) {
        extraImportDirectories = new ArrayList<>(ExtraImportDirectories.persisted(directories, instanceDir));
    }

    public FilterPersistence filterPersistence() {
        return filterPersistence == null ? FilterPersistence.NOT_PERSISTED : filterPersistence;
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
        return filter == null ? SearchFilter.defaults() : filter;
    }

    public void setPersistedFilter(SearchFilter next) {
        this.filter = next == null ? SearchFilter.defaults() : next;
    }

    public static int clampFontSize(int fontSize) {
        return Math.clamp(fontSize, MIN_MESSAGE_FONT_SIZE, MAX_MESSAGE_FONT_SIZE);
    }

    public void save() {
        normalize();
        if (handler != null && this == handler.instance()) {
            handler.save();
            return;
        }
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson().toJson(this, writer);
            }
        } catch (IOException error) {
            AllTheLogsClient.LOGGER.warn("Could not write {}", file, error);
        }
    }

    void normalize() {
        extraImportDirectories = extraImportDirectories == null
            ? new ArrayList<>()
            : new ArrayList<>(ExtraImportDirectories.persisted(extraImportDirectories, null));
        if (filterPersistence == null) filterPersistence = FilterPersistence.NOT_PERSISTED;
        messageFontSize = clampFontSize(messageFontSize == 0 ? DEFAULT_MESSAGE_FONT_SIZE : messageFontSize);
        if (filter == null) filter = SearchFilter.defaults();
    }

    static Gson gson() {
        return gsonBuilder().create();
    }

    private static GsonBuilder gsonBuilder() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(SearchFilter.class, new SearchFilterJson())
            .registerTypeAdapter(FilterPersistence.class, new FilterPersistenceJson());
    }

    private static ConfigClassHandler<AllTheLogsConfig> handler() {
        if (handler == null) {
            handler = ConfigClassHandler.createBuilder(AllTheLogsConfig.class)
                .id(Identifier.parse("allthelogs"))
                .serializer(AllTheLogsConfig::yaclSerializer)
                .build();
        }
        return handler;
    }

    /**
     * YACL writes through its own serializer, so normalize before save and after load the same way
     * {@link #save()} does for tests and {@link FilterPersistence#remember}.
     */
    private static ConfigSerializer<AllTheLogsConfig> yaclSerializer(ConfigClassHandler<AllTheLogsConfig> config) {
        ConfigSerializer<AllTheLogsConfig> gson = GsonConfigSerializerBuilder.create(config)
            .setPath(defaultPath())
            .appendGsonBuilder(builder -> builder
                .disableHtmlEscaping()
                .registerTypeAdapter(SearchFilter.class, new SearchFilterJson())
                .registerTypeAdapter(FilterPersistence.class, new FilterPersistenceJson()))
            .build();
        return new ConfigSerializer<>(config) {
            @Override
            public void save() {
                config.instance().normalize();
                gson.save();
            }

            @Override
            public LoadResult loadSafely(Map<ConfigField<?>, FieldAccess<?>> snapshot) {
                LoadResult result = gson.loadSafely(snapshot);
                config.instance().normalize();
                return result;
            }
        };
    }

    private static final class FilterPersistenceJson
        implements JsonSerializer<FilterPersistence>, JsonDeserializer<FilterPersistence> {
        @Override
        public JsonElement serialize(FilterPersistence src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize((src == null ? FilterPersistence.NOT_PERSISTED : src).name());
        }

        @Override
        public FilterPersistence deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json == null || json.isJsonNull()) return FilterPersistence.NOT_PERSISTED;
            try {
                return FilterPersistence.fromConfig(json.getAsString());
            } catch (RuntimeException ignored) {
                return FilterPersistence.NOT_PERSISTED;
            }
        }
    }

    private static final class SearchFilterJson implements JsonSerializer<SearchFilter>, JsonDeserializer<SearchFilter> {
        @Override
        public JsonElement serialize(SearchFilter src, Type typeOfSrc, JsonSerializationContext context) {
            return FilterPersistence.toJson(src);
        }

        @Override
        public SearchFilter deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
            if (json == null || !json.isJsonObject()) return SearchFilter.defaults();
            return FilterPersistence.fromJson(json.getAsJsonObject());
        }
    }
}
