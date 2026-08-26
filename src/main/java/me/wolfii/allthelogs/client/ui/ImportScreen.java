package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.CommonLogLocations;
import me.wolfii.allthelogs.client.NativeFilePicker;
import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

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

        root.child(UIComponents.label(Component.translatable("allthelogs.screen.import")));

        FlowLayout pathRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        pathRow.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        pathBox = UIComponents.textBox(Sizing.expand(), "");
        pathBox.setMaxLength(1024);
        pathRow.child(pathBox);
        pathRow.child(UIComponents.button(Component.translatable("allthelogs.import.browse"), button -> {
            DropdownComponent.openContextMenu(this, root, FlowLayout::child,
                button.x(), button.y() + button.height(),
                dropdown -> dropdown
                    .button(Component.translatable("allthelogs.import.browse.folder"), d ->
                        NativeFilePicker.pickFolder(currentPath(), this::setPath))
                    .button(Component.translatable("allthelogs.import.browse.archive"), d ->
                        NativeFilePicker.pickArchive(currentPath(), this::setPath)));
        }));
        root.child(pathRow);

        List<CommonLogLocations.Location> common = CommonLogLocations.defaults();
        if (!common.isEmpty()) {
            root.child(UIComponents.label(Component.translatable("allthelogs.import.common")));
            FlowLayout locations = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
            locations.gap(2);
            for (CommonLogLocations.Location location : common) {
                locations.child(UIComponents.button(Component.literal(location.displayName()),
                    button -> applyLocation(location)));
            }
            root.child(UIContainers.verticalScroll(Sizing.fill(), Sizing.fixed(90), locations)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat()));
        }

        root.child(UIContainers.collapsible(Sizing.fill(), Sizing.content(),
                Component.translatable("allthelogs.import.advanced"), false)
            .child(buildAdvanced()));

        status = UIComponents.label(Component.empty());
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        actions.child(UIComponents.button(Component.translatable("allthelogs.import.start"), button -> startImport()));
        actions.child(status);
        root.child(actions);
    }

    private FlowLayout buildAdvanced() {
        FlowLayout advanced = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        advanced.gap(4).padding(Insets.of(4));

        CheckboxComponent recursiveBox = UIComponents.checkbox(Component.translatable("allthelogs.import.recursive"));
        recursiveBox.checked(recursive);
        recursiveBox.onChanged(value -> recursive = value);
        advanced.child(recursiveBox);

        CheckboxComponent nestedBox = UIComponents.checkbox(Component.translatable("allthelogs.import.nested_archives"));
        nestedBox.checked(nestedArchives);
        nestedBox.onChanged(value -> nestedArchives = value);
        advanced.child(nestedBox);

        CheckboxComponent skipBox = UIComponents.checkbox(Component.translatable("allthelogs.import.skip_imported"));
        skipBox.checked(skipAlreadyImported);
        skipBox.onChanged(value -> skipAlreadyImported = value);
        advanced.child(skipBox);

        advanced.child(field("allthelogs.import.path_matcher", pathMatcher, value -> pathMatcher = value));
        advanced.child(field("allthelogs.import.timezone", timezone, value -> timezone = value));
        advanced.child(field("allthelogs.import.parallelism", String.valueOf(parallelism), value -> {
            try {
                parallelism = Math.max(1, Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
            }
        }));
        return advanced;
    }

    private FlowLayout field(String key, String value, java.util.function.Consumer<String> onChange) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(128);
        box.onChanged().subscribe(text -> onChange.accept(text));
        row.child(box);
        return row;
    }

    private void applyLocation(CommonLogLocations.Location location) {
        location.firstExisting().ifPresentOrElse(this::setPath, () -> {
            if (!location.resolveAll().isEmpty()) {
                setPath(location.resolveAll().getFirst());
            }
        });
        ImportOptions options = location.suggestedOptions();
        recursive = options.recursive();
        nestedArchives = options.nestedArchives();
        skipAlreadyImported = options.skipAlreadyImported();
        pathMatcher = options.pathMatcher() == null ? "" : options.pathMatcher();
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
        status.text(Component.translatable("allthelogs.status.importing"));
        java.util.function.Consumer<ImportProgress> progress = snapshot ->
            Minecraft.getInstance().execute(() -> status.text(Component.literal(
                snapshot.completedFiles() + "/" + snapshot.discoveredFiles())));
        var future = Files.isRegularFile(path)
            ? AllTheLogsClient.worker().importArchive(path, options(), progress)
            : AllTheLogsClient.worker().importDirectory(path, options(), progress);
        future.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("Import failed", error);
                status.text(Component.translatable("allthelogs.status.error"));
                return;
            }
            status.text(Component.translatable("allthelogs.status.imported",
                result.importedFiles(), result.importedEntries()));
        }));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
