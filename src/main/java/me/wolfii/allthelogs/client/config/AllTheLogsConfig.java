package me.wolfii.allthelogs.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.ConfigSerializer;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User settings stored as {@code config/allthelogs.json}. Extra logs folders and browser chrome are
 * edited through a YACL autogen screen. The current instance is always scanned and is never written here.
 */
public class AllTheLogsConfig {
    public static final String FILE_NAME = "allthelogs.json";
    public static final int DEFAULT_MESSAGE_FONT_SIZE = MessageListLayout.ROW_HEIGHT;
    public static final int MIN_MESSAGE_FONT_SIZE = 6;
    public static final int MAX_MESSAGE_FONT_SIZE = 24;
    public static final int MIN_CONTEXT_MESSAGE_BRIGHTNESS = 10;
    public static final int MAX_CONTEXT_MESSAGE_BRIGHTNESS = 100;
    public static final int DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS = 40;

    private static ConfigClassHandler<AllTheLogsConfig> handler;

    private transient Path file;

    @AutoGen(category = "import")
    @ListGroup(valueFactory = StringListFactory.class, controllerFactory = StringListFactory.class)
    @SerialEntry
    public List<String> extraImportDirectories = new ArrayList<>();

    @AutoGen(category = "browser")
    @IntField(min = 0, max = SearchFilter.MAX_CONTEXT_LINES, format = "%d lines")
    @SerialEntry(required = false)
    public int defaultContextLines = SearchFilter.DEFAULT_CONTEXT_LINES;

    @AutoGen(category = "browser")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    @SerialEntry
    public boolean hideImportButton;

    @AutoGen(category = "browser")
    @IntSlider(min = MIN_MESSAGE_FONT_SIZE, max = MAX_MESSAGE_FONT_SIZE, step = 1)
    @SerialEntry
    public int messageFontSize = DEFAULT_MESSAGE_FONT_SIZE;

    @AutoGen(category = "browser")
    @IntSlider(min = MIN_CONTEXT_MESSAGE_BRIGHTNESS, max = MAX_CONTEXT_MESSAGE_BRIGHTNESS, step = 1, format = "%d%%")
    @SerialEntry(required = false)
    public int contextMessageBrightness = DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS;

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
            JsonElement parsed = JsonParser.parseReader(reader);
            AllTheLogsConfig loaded = gson().fromJson(parsed, AllTheLogsConfig.class);
            if (loaded != null) {
                loaded.file = file;
                if (parsed == null || !parsed.isJsonObject()
                    || !parsed.getAsJsonObject().has("defaultContextLines")) {
                    loaded.defaultContextLines = SearchFilter.DEFAULT_CONTEXT_LINES;
                }
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

    public int defaultContextLines() {
        return clampContextLines(defaultContextLines);
    }

    public void setDefaultContextLines(int defaultContextLines) {
        this.defaultContextLines = clampContextLines(defaultContextLines);
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

    public int contextMessageBrightness() {
        return clampContextMessageBrightness(contextMessageBrightness);
    }

    public void setContextMessageBrightness(int contextMessageBrightness) {
        this.contextMessageBrightness = clampContextMessageBrightness(contextMessageBrightness);
    }

    /**
     * Brightness applied to context-line message text. Uses the default until config has been loaded.
     */
    public static int currentContextMessageBrightness() {
        if (handler == null) return DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS;
        AllTheLogsConfig instance = handler.instance();
        return instance == null ? DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS : instance.contextMessageBrightness();
    }

    public static int clampFontSize(int fontSize) {
        return Math.clamp(fontSize, MIN_MESSAGE_FONT_SIZE, MAX_MESSAGE_FONT_SIZE);
    }

    public static int clampContextLines(int contextLines) {
        return Math.clamp(contextLines, 0, SearchFilter.MAX_CONTEXT_LINES);
    }

    public static int clampContextMessageBrightness(int percent) {
        return Math.clamp(percent, MIN_CONTEXT_MESSAGE_BRIGHTNESS, MAX_CONTEXT_MESSAGE_BRIGHTNESS);
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
        defaultContextLines = clampContextLines(defaultContextLines);
        messageFontSize = clampFontSize(messageFontSize == 0 ? DEFAULT_MESSAGE_FONT_SIZE : messageFontSize);
        contextMessageBrightness = clampContextMessageBrightness(
            contextMessageBrightness == 0 ? DEFAULT_CONTEXT_MESSAGE_BRIGHTNESS : contextMessageBrightness);
    }

    static Gson gson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
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
     * YACL writes through its own serializer, so normalize extra logs paths before save and after load.
     */
    private static ConfigSerializer<AllTheLogsConfig> yaclSerializer(ConfigClassHandler<AllTheLogsConfig> config) {
        ConfigSerializer<AllTheLogsConfig> gson = GsonConfigSerializerBuilder.create(config)
            .setPath(defaultPath())
            .appendGsonBuilder(builder -> builder.disableHtmlEscaping())
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
}
