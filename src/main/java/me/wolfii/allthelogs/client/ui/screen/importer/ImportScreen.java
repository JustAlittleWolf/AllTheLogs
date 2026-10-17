package me.wolfii.allthelogs.client.ui.screen.importer;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.CollapsibleContainer;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.files.CommonLogLocations;
import me.wolfii.allthelogs.client.files.ImportPaths;
import me.wolfii.allthelogs.client.files.ImportTimezones;
import me.wolfii.allthelogs.client.files.NativeFilePicker;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.text.WrappedTooltip;
import me.wolfii.allthelogs.data.importer.ImportOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

/**
 * Import a folder or archive into the log store. Launcher directory shortcuts from
 * {@link CommonLogLocations#defaults()} fill both the path and advanced options when chosen.
 * Folder and archive pickers only set the path while Advanced is expanded; if it is collapsed
 * they restore {@link ImportPaths#formDefaults()}. Choosing Custom path always restores those
 * defaults.
 */
public final class ImportScreen extends BaseOwoScreen<StackLayout> {
    private static final int MIN_PARALLELISM = 1;

    private final Screen parent;
    private CommonLocationMenu commonLocations;
    private TimezonePicker timezones;
    private TextBoxComponent pathBox;
    private LabelComponent status;
    private boolean recursive = true;
    private boolean nestedArchives = true;
    private boolean skipAlreadyImported = true;
    private boolean updateMetadataOnly;
    private boolean overwriteFormatting;
    private boolean overwriteMinecraftUser;
    private boolean overwriteMinecraftServer;
    private boolean overwriteMinecraftVersion;
    private boolean overwriteLive;
    private String pathMatcher = "";
    private ZoneId timezone = ZoneId.systemDefault();
    private boolean summerTime = ImportTimezones.observesDaylightSaving(timezone);
    private int parallelism = ImportOptions.defaultParallelism();
    private CheckboxComponent recursiveBox;
    private CheckboxComponent nestedBox;
    private CheckboxComponent skipBox;
    private CheckboxComponent updateMetadataBox;
    private CheckboxComponent overwriteFormattingBox;
    private CheckboxComponent overwriteMinecraftUserBox;
    private CheckboxComponent overwriteMinecraftServerBox;
    private CheckboxComponent overwriteMinecraftVersionBox;
    private CheckboxComponent overwriteLiveBox;
    private FlowLayout advancedOptions;
    private FlowLayout metadataFields;
    private CheckboxComponent summerTimeBox;
    private TextBoxComponent pathMatcherBox;
    private DiscreteSliderComponent parallelismSlider;
    private CollapsibleContainer advanced;

    public ImportScreen(Screen parent) {
        super(Component.translatable("allthelogs.screen.import"));
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        content.gap(8);
        content.allowOverflow(true);
        content.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(16))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        FlowLayout form = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        form.gap(8);
        form.padding(Insets.right(12));

        form.child(UIComponents.label(Component.translatable("allthelogs.screen.import")));
        LabelComponent description = UIComponents.label(Component.translatable("allthelogs.import.description"));
        description.color(Color.ofRgb(0xA0A0A0));
        description.maxWidth(Math.max(160, this.width - 64));
        form.child(description);

        form.child(UIComponents.label(Component.translatable("allthelogs.import.source_path")));
        pathBox = UIComponents.textBox(Sizing.fill(), "");
        pathBox.setMaxLength(1024);
        form.child(pathBox);

        FlowLayout pathRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        pathRow.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        pathRow.child(UIComponents.button(Component.translatable("allthelogs.import.from_folder"),
            button -> NativeFilePicker.pickFolder(currentPath(), this::setPickedPath)));
        pathRow.child(UIComponents.button(Component.translatable("allthelogs.import.from_archive"),
            button -> NativeFilePicker.pickArchive(currentPath(), this::setPickedPath)));
        commonLocations = new CommonLocationMenu(root, () -> this.width, () -> this.height, this::applyLocation);
        pathRow.child(commonLocations.button());
        form.child(pathRow);

        LabelComponent dropHint = UIComponents.label(Component.translatable("allthelogs.import.drop_hint"));
        dropHint.color(Color.ofRgb(0xA0A0A0));
        form.child(dropHint);

        advanced = UIContainers.collapsible(Sizing.fill(), Sizing.content(),
            Component.translatable("allthelogs.import.advanced"), false);
        advanced.child(buildAdvanced(root));
        form.child(advanced);

        ScrollContainer<FlowLayout> scroll = OverflowScrollbar.vertical(
            Sizing.fill(), Sizing.expand(), form);
        scroll.padding(Insets.right(6));
        content.child(scroll);

        status = UIComponents.label(Component.empty());
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        actions.child(paddedButton(Component.translatable("allthelogs.import.start"), button -> startImport()));
        actions.child(status.horizontalSizing(Sizing.expand()));
        actions.child(paddedButton(Component.translatable("gui.done"), button -> onClose()));
        content.child(actions);
        root.child(content);
    }

    private ButtonComponent paddedButton(Component label, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(label, onPress);
        button.horizontalSizing(Sizing.fixed(Math.max(40, this.font.width(label) + 32)));
        return button;
    }

    private FlowLayout buildAdvanced(StackLayout overlays) {
        advancedOptions = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        advancedOptions.gap(4).padding(Insets.of(4));

        recursiveBox = optionCheckbox("allthelogs.import.recursive", recursive, value -> recursive = value);
        advancedOptions.child(recursiveBox);

        nestedBox = optionCheckbox("allthelogs.import.nested_archives", nestedArchives, value -> nestedArchives = value);
        advancedOptions.child(nestedBox);

        skipBox = optionCheckbox("allthelogs.import.skip_imported", skipAlreadyImported,
            value -> skipAlreadyImported = value);
        advancedOptions.child(skipBox);

        updateMetadataBox = optionCheckbox("allthelogs.import.update_metadata", updateMetadataOnly,
            this::setUpdateMetadataOnly);
        advancedOptions.child(updateMetadataBox);
        metadataFields = metadataFieldSelect();
        if (updateMetadataOnly) advancedOptions.child(metadataFields);

        advancedOptions.child(pathMatcherField());

        summerTimeBox = optionCheckbox("allthelogs.import.summer_time", summerTime, value -> summerTime = value);
        timezones = new TimezonePicker(overlays, () -> this.width, () -> this.height, this::selectTimezone);
        advancedOptions.child(timezones.row(timezone, summerTimeBox));

        advancedOptions.child(threadsSlider());
        return advancedOptions;
    }

    private void setUpdateMetadataOnly(boolean value) {
        updateMetadataOnly = value;
        if (advancedOptions == null || metadataFields == null) return;
        boolean present = advancedOptions.children().contains(metadataFields);
        if (value && !present) {
            int index = advancedOptions.children().indexOf(updateMetadataBox) + 1;
            advancedOptions.child(index, metadataFields);
        } else if (!value && present) {
            advancedOptions.removeChild(metadataFields);
        }
    }

    /**
     * Overwrite choices under "Update metadata". Missing values are always filled in, so these only decide
     * which existing values may be replaced. "Also live messages" only shows while one of them is checked.
     */
    private FlowLayout metadataFieldSelect() {
        FlowLayout fields = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        fields.gap(4).padding(Insets.left(16));
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(12).verticalAlignment(VerticalAlignment.CENTER);
        overwriteFormattingBox = optionCheckbox("allthelogs.import.overwrite.formatting", overwriteFormatting,
            value -> {
                overwriteFormatting = value;
                syncOverwriteLive();
            });
        overwriteMinecraftUserBox = optionCheckbox("allthelogs.import.overwrite.user", overwriteMinecraftUser,
            value -> {
                overwriteMinecraftUser = value;
                syncOverwriteLive();
            });
        overwriteMinecraftServerBox = optionCheckbox("allthelogs.import.overwrite.server", overwriteMinecraftServer,
            value -> {
                overwriteMinecraftServer = value;
                syncOverwriteLive();
            });
        row.child(overwriteFormattingBox);
        row.child(overwriteMinecraftUserBox);
        fields.child(row);
        FlowLayout secondRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        secondRow.gap(12).verticalAlignment(VerticalAlignment.CENTER);
        secondRow.child(overwriteMinecraftServerBox);
        overwriteMinecraftVersionBox = optionCheckbox("allthelogs.import.overwrite.version", overwriteMinecraftVersion,
            value -> {
                overwriteMinecraftVersion = value;
                syncOverwriteLive();
            });
        secondRow.child(overwriteMinecraftVersionBox);
        fields.child(secondRow);
        overwriteLiveBox = optionCheckbox("allthelogs.import.overwrite.live", overwriteLive, value -> overwriteLive = value);
        if (overwritesAny()) fields.child(overwriteLiveBox);
        return fields;
    }

    private boolean overwritesAny() {
        return overwriteFormatting || overwriteMinecraftUser || overwriteMinecraftServer || overwriteMinecraftVersion;
    }

    private void syncOverwriteLive() {
        if (metadataFields == null || overwriteLiveBox == null) return;
        boolean present = metadataFields.children().contains(overwriteLiveBox);
        if (overwritesAny() && !present) metadataFields.child(overwriteLiveBox);
        else if (!overwritesAny() && present) metadataFields.removeChild(overwriteLiveBox);
    }

    private CheckboxComponent optionCheckbox(String key, boolean checked, Consumer<Boolean> onChanged) {
        CheckboxComponent box = UIComponents.checkbox(Component.translatable(key));
        box.checked(checked);
        box.onChanged(onChanged::accept);
        box.tooltip(WrappedTooltip.of(Component.translatable(key + ".hint")));
        return box;
    }

    private FlowLayout pathMatcherField() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        LabelComponent label = UIComponents.label(Component.translatable("allthelogs.import.path_matcher"));
        label.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.import.path_matcher.hint")));
        row.child(label);
        pathMatcherBox = UIComponents.textBox(Sizing.fill(), pathMatcher);
        pathMatcherBox.setMaxLength(128);
        pathMatcherBox.onChanged().subscribe(value -> pathMatcher = value);
        pathMatcherBox.tooltip(WrappedTooltip.of(Component.translatable("allthelogs.import.path_matcher.hint")));
        row.child(pathMatcherBox);
        return row;
    }

    private FlowLayout threadsSlider() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.import.threads")));
        int max = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);
        parallelismSlider = UIComponents.discreteSlider(Sizing.fill(), MIN_PARALLELISM, max);
        parallelismSlider.decimalPlaces(0);
        parallelismSlider.snap(true);
        parallelismSlider.setFromDiscreteValue(Math.min(max, Math.max(MIN_PARALLELISM, parallelism)));
        parallelismSlider.message(value -> Component.translatable("allthelogs.import.threads.value", value));
        parallelismSlider.onChanged().subscribe(value -> parallelism = Math.max(MIN_PARALLELISM, (int) Math.round(value)));
        row.child(parallelismSlider);
        return row;
    }

    private void selectTimezone(ImportTimezones.Choice choice) {
        boolean zoneChanged = !choice.zone().equals(timezone);
        timezone = choice.zone();
        if (zoneChanged) {
            summerTime = ImportTimezones.observesDaylightSaving(timezone);
            if (summerTimeBox != null) summerTimeBox.checked(summerTime);
        }
    }

    private void applyLocation(CommonLogLocations.Location location) {
        if (location == null) {
            applyOptions(ImportPaths.formDefaults());
            return;
        }
        setPath(location.preferredPath());
        applyOptions(location.suggestedOptions());
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        ImportPaths.fromDropped(paths).ifPresent(this::setPath);
    }

    private void applyOptions(ImportOptions options) {
        recursive = options.recursive();
        nestedArchives = options.nestedArchives();
        skipAlreadyImported = options.skipAlreadyImported();
        updateMetadataOnly = options.updateMetadataOnly();
        overwriteFormatting = options.overwriteFormatting();
        overwriteMinecraftUser = options.overwriteMinecraftUser();
        overwriteMinecraftServer = options.overwriteMinecraftServer();
        overwriteMinecraftVersion = options.overwriteMinecraftVersion();
        overwriteLive = options.overwriteLive();
        pathMatcher = options.pathMatcher() == null ? "" : options.pathMatcher();
        if (recursiveBox != null) recursiveBox.checked(recursive);
        if (nestedBox != null) nestedBox.checked(nestedArchives);
        if (skipBox != null) skipBox.checked(skipAlreadyImported);
        if (overwriteFormattingBox != null) overwriteFormattingBox.checked(overwriteFormatting);
        if (overwriteMinecraftUserBox != null) overwriteMinecraftUserBox.checked(overwriteMinecraftUser);
        if (overwriteMinecraftServerBox != null) overwriteMinecraftServerBox.checked(overwriteMinecraftServer);
        if (overwriteMinecraftVersionBox != null) overwriteMinecraftVersionBox.checked(overwriteMinecraftVersion);
        if (overwriteLiveBox != null) overwriteLiveBox.checked(overwriteLive);
        syncOverwriteLive();
        if (updateMetadataBox != null) updateMetadataBox.checked(updateMetadataOnly);
        setUpdateMetadataOnly(updateMetadataOnly);
        if (pathMatcherBox != null) pathMatcherBox.text(pathMatcher);
    }

    private void setPickedPath(Path path) {
        setPath(path);
        if (advanced == null || !advanced.expanded()) {
            applyOptions(ImportPaths.formDefaults());
        }
    }

    private void setPath(Path path) {
        pathBox.text(path.toAbsolutePath().normalize().toString());
    }

    private Path currentPath() {
        String text = pathBox.getValue();
        if (text == null || text.isBlank()) {
            return AllTheLogsPaths.gameDirectory();
        }
        return Path.of(text);
    }

    private ImportOptions options() {
        ZoneId zone = ImportTimezones.parse(timezones == null ? "" : timezones.text()).orElse(timezone);
        ImportOptions options = ImportOptions.defaults()
            .withRecursive(recursive)
            .withNestedArchives(nestedArchives)
            .withSkipAlreadyImported(skipAlreadyImported)
            .withUpdateMetadataOnly(updateMetadataOnly)
            .withOverwriteFormatting(overwriteFormatting)
            .withOverwriteMinecraftUser(overwriteMinecraftUser)
            .withOverwriteMinecraftServer(overwriteMinecraftServer)
            .withOverwriteMinecraftVersion(overwriteMinecraftVersion)
            .withOverwriteLive(overwriteLive && (overwriteFormatting || overwriteMinecraftUser
                || overwriteMinecraftServer || overwriteMinecraftVersion))
            .withParallelism(parallelism)
            .withTimezone(ImportTimezones.forImport(zone, summerTime));
        if (!pathMatcher.isBlank()) {
            options = options.withPathMatcher(pathMatcher);
        }
        return options;
    }

    private void startImport() {
        Path path = currentPath();
        String text = pathBox.getValue();
        if (text == null || text.isBlank()) {
            status.text(Component.translatable("allthelogs.import.missing_path"));
            return;
        }
        if (timezones != null && ImportTimezones.parse(timezones.text()).isEmpty()) {
            status.text(Component.translatable("allthelogs.import.invalid_timezone"));
            return;
        }
        Minecraft.getInstance().gui.setScreen(new ImportProgressScreen(this, path, options(), Files.isRegularFile(path)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) {
            return;
        }
        this.extractPanorama(graphics, delta);
    }

    @Override
    public void onClose() {
        if (commonLocations != null) commonLocations.close();
        if (timezones != null) timezones.close();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
