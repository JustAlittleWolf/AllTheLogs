package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.files.CommonLogLocations;
import me.wolfii.allthelogs.client.files.ImportPaths;
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
 * {@link CommonLogLocations#defaults()} fill both the path and advanced options when any are defined.
 */
public final class ImportScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private TextBoxComponent pathBox;
    private LabelComponent status;
    private boolean recursive = true;
    private boolean nestedArchives = true;
    private boolean skipAlreadyImported = true;
    private String pathMatcher = "";
    private String timezone = ZoneId.systemDefault().getId();
    private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
    private CheckboxComponent recursiveBox;
    private CheckboxComponent nestedBox;
    private CheckboxComponent skipBox;
    private TextBoxComponent pathMatcherBox;

    public ImportScreen(Screen parent) {
        super(Component.translatable("allthelogs.screen.import"));
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(8);
        root.surface(Surface.VANILLA_TRANSLUCENT)
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

        FlowLayout pathRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        pathRow.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        pathRow.child(UIComponents.button(Component.translatable("allthelogs.import.from_folder"),
            button -> NativeFilePicker.pickFolder(currentPath(), this::setFolder)));
        pathRow.child(UIComponents.button(Component.translatable("allthelogs.import.from_archive"),
            button -> NativeFilePicker.pickArchive(currentPath(), this::setPath)));
        form.child(pathRow);

        LabelComponent dropHint = UIComponents.label(Component.translatable("allthelogs.import.drop_hint"));
        dropHint.color(Color.ofRgb(0xA0A0A0));
        form.child(dropHint);

        List<CommonLogLocations.Location> common = CommonLogLocations.defaults();
        if (!common.isEmpty()) {
            form.child(UIComponents.label(Component.translatable("allthelogs.import.common")));
            FlowLayout locations = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
            locations.gap(2);
            for (CommonLogLocations.Location location : common) {
                locations.child(UIComponents.button(Component.literal(location.displayName()),
                    button -> applyLocation(location)));
            }
            int locationHeight = Math.min(90, Math.max(24, common.size() * 22));
            ScrollContainer<FlowLayout> locationScroll = UIContainers.verticalScroll(
                Sizing.fill(), Sizing.fixed(locationHeight), locations);
            locationScroll.scrollbar(OverflowScrollbar.vanillaFlat());
            form.child(locationScroll);
        }

        form.child(UIContainers.collapsible(Sizing.fill(), Sizing.content(),
                Component.translatable("allthelogs.import.advanced"), false)
            .child(buildAdvanced()));

        ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(
            Sizing.fill(), Sizing.expand(), form);
        scroll.scrollbar(OverflowScrollbar.vanillaFlat());
        scroll.padding(Insets.right(6));
        root.child(scroll);

        status = UIComponents.label(Component.empty());
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        actions.child(UIComponents.button(Component.translatable("allthelogs.import.start"), button -> startImport()));
        actions.child(UIComponents.button(Component.translatable("allthelogs.done"),
            button -> Minecraft.getInstance().gui.setScreen(parent)));
        actions.child(status);
        root.child(actions);
    }

    private FlowLayout buildAdvanced() {
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

        advanced.child(field("allthelogs.import.path_matcher", pathMatcher, value -> pathMatcher = value, box -> pathMatcherBox = box));
        advanced.child(field("allthelogs.import.timezone", timezone, value -> timezone = value, null));
        advanced.child(field("allthelogs.import.parallelism", String.valueOf(parallelism), value -> {
            try {
                parallelism = Math.max(1, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
            }
        }, null));
        return advanced;
    }

    private FlowLayout field(String key, String value, Consumer<String> onChange, Consumer<TextBoxComponent> bind) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(128);
        box.onChanged().subscribe(onChange::accept);
        if (bind != null) bind.accept(box);
        row.child(box);
        return row;
    }

    private void applyLocation(CommonLogLocations.Location location) {
        location.firstExisting().ifPresentOrElse(this::setPath, () -> {
            if (!location.resolveAll().isEmpty()) {
                setPath(location.resolveAll().getFirst());
            }
        });
        applyOptions(location.suggestedOptions());
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        ImportPaths.fromDropped(paths).ifPresent(path -> {
            if (Files.isDirectory(path)) {
                setFolder(path);
            } else {
                setPath(path);
            }
        });
    }

    private void setFolder(Path path) {
        applyOptions(ImportPaths.optionsForFolder(path));
        setPath(path);
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
        ImportOptions options = ImportOptions.defaults()
            .withRecursive(recursive)
            .withNestedArchives(nestedArchives)
            .withSkipAlreadyImported(skipAlreadyImported)
            .withParallelism(parallelism)
            .withTimezone(timezone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timezone));
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
        Minecraft.getInstance().gui.setScreen(new ImportProgressScreen(this, path, options(), Files.isRegularFile(path)));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
