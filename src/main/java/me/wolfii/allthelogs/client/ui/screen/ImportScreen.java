package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
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
import me.wolfii.allthelogs.data.ImportOptions;
import net.minecraft.client.Minecraft;
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
 * Folder and archive pickers only set the path; advanced options stay as the user left them.
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
    private String pathMatcher = "";
    private ZoneId timezone = ZoneId.systemDefault();
    private boolean summerTime = ImportTimezones.observesDaylightSaving(timezone);
    private int parallelism = Math.max(MIN_PARALLELISM, Runtime.getRuntime().availableProcessors());
    private CheckboxComponent recursiveBox;
    private CheckboxComponent nestedBox;
    private CheckboxComponent skipBox;
    private CheckboxComponent summerTimeBox;
    private TextBoxComponent pathMatcherBox;
    private DiscreteSliderComponent parallelismSlider;

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
            button -> NativeFilePicker.pickFolder(currentPath(), this::setPath)));
        pathRow.child(UIComponents.button(Component.translatable("allthelogs.import.from_archive"),
            button -> NativeFilePicker.pickArchive(currentPath(), this::setPath)));
        commonLocations = new CommonLocationMenu(root, () -> this.width, () -> this.height, this::applyLocation);
        pathRow.child(commonLocations.button());
        form.child(pathRow);

        LabelComponent dropHint = UIComponents.label(Component.translatable("allthelogs.import.drop_hint"));
        dropHint.color(Color.ofRgb(0xA0A0A0));
        form.child(dropHint);

        form.child(UIContainers.collapsible(Sizing.fill(), Sizing.content(),
                Component.translatable("allthelogs.import.advanced"), false)
            .child(buildAdvanced(root)));

        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(
            Sizing.fill(), Sizing.expand(), form);
        scroll.scrollbar(OverflowScrollbar.vanillaFlat());
        scroll.padding(Insets.right(6));
        content.child(scroll);

        status = UIComponents.label(Component.empty());
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        actions.child(paddedButton(Component.translatable("allthelogs.import.start"), button -> startImport()));
        actions.child(status.horizontalSizing(Sizing.expand()));
        actions.child(paddedButton(Component.translatable("allthelogs.done"),
            button -> Minecraft.getInstance().gui.setScreen(parent)));
        content.child(actions);
        root.child(content);
    }

    private ButtonComponent paddedButton(Component label, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(label, onPress);
        button.horizontalSizing(Sizing.fixed(Math.max(40, this.font.width(label) + 32)));
        return button;
    }

    private FlowLayout buildAdvanced(StackLayout overlays) {
        FlowLayout advanced = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        advanced.gap(4).padding(Insets.of(4));

        recursiveBox = UIComponents.checkbox(Component.translatable("allthelogs.import.recursive"));
        recursiveBox.checked(recursive);
        recursiveBox.onChanged(value -> recursive = value);
        advanced.child(recursiveBox);

        nestedBox = UIComponents.checkbox(Component.translatable("allthelogs.import.nested_archives"));
        nestedBox.checked(nestedArchives);
        nestedBox.onChanged(value -> nestedArchives = value);
        advanced.child(nestedBox);

        skipBox = UIComponents.checkbox(Component.translatable("allthelogs.import.skip_imported"));
        skipBox.checked(skipAlreadyImported);
        skipBox.onChanged(value -> skipAlreadyImported = value);
        advanced.child(skipBox);

        advanced.child(pathMatcherField());

        timezones = new TimezonePicker(overlays, () -> this.width, () -> this.height, this::selectTimezone);
        advanced.child(timezones.row(timezone));

        summerTimeBox = UIComponents.checkbox(Component.translatable("allthelogs.import.summer_time"));
        summerTimeBox.checked(summerTime);
        summerTimeBox.onChanged(value -> summerTime = value);
        summerTimeBox.tooltip(Component.translatable("allthelogs.import.summer_time.hint"));
        advanced.child(summerTimeBox);

        advanced.child(parallelismSlider());
        return advanced;
    }

    private FlowLayout pathMatcherField() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        LabelComponent label = UIComponents.label(Component.translatable("allthelogs.import.path_matcher"));
        label.tooltip(Component.translatable("allthelogs.import.path_matcher.hint"));
        row.child(label);
        pathMatcherBox = UIComponents.textBox(Sizing.fill(), pathMatcher);
        pathMatcherBox.setMaxLength(128);
        pathMatcherBox.onChanged().subscribe(value -> pathMatcher = value);
        row.child(pathMatcherBox);
        LabelComponent hint = UIComponents.label(Component.translatable("allthelogs.import.path_matcher.hint"));
        hint.color(Color.ofRgb(0xA0A0A0));
        hint.maxWidth(Math.max(160, this.width - 80));
        row.child(hint);
        return row;
    }

    private FlowLayout parallelismSlider() {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.import.parallelism")));
        int max = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);
        parallelismSlider = UIComponents.discreteSlider(Sizing.fill(), MIN_PARALLELISM, max);
        parallelismSlider.decimalPlaces(0);
        parallelismSlider.snap(true);
        parallelismSlider.setFromDiscreteValue(Math.min(max, Math.max(MIN_PARALLELISM, parallelism)));
        parallelismSlider.message(value -> Component.translatable("allthelogs.import.parallelism.value", value));
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
        pathMatcher = options.pathMatcher() == null ? "" : options.pathMatcher();
        if (recursiveBox != null) recursiveBox.checked(recursive);
        if (nestedBox != null) nestedBox.checked(nestedArchives);
        if (skipBox != null) skipBox.checked(skipAlreadyImported);
        if (pathMatcherBox != null) pathMatcherBox.text(pathMatcher);
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
    public void onClose() {
        if (commonLocations != null) commonLocations.close();
        if (timezones != null) timezones.close();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
