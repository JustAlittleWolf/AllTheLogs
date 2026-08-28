package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.ui.text.ImportProgressText;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportPhase;
import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.ImportResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Full-screen import progress: a bar, file counts, and the log currently being read.
 */
public final class ImportProgressScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final Path path;
    private final ImportOptions options;
    private final boolean archive;
    private LabelComponent heading;
    private LabelComponent counts;
    private LabelComponent current;
    private BoxComponent fill;
    private ButtonComponent done;
    private ButtonComponent cancel;
    private boolean finished;
    private boolean cancelled;

    public ImportProgressScreen(Screen parent, Path path, ImportOptions options, boolean archive) {
        super(Component.translatable("allthelogs.screen.import.progress"));
        this.parent = parent;
        this.path = path;
        this.options = options;
        this.archive = archive;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(12);
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(32))
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout card = UIContainers.verticalFlow(Sizing.fill(70), Sizing.content());
        card.gap(10)
            .padding(Insets.of(16))
            .surface(PanelSurfaces.card())
            .horizontalAlignment(HorizontalAlignment.LEFT);

        heading = UIComponents.label(Component.translatable("allthelogs.status.importing"));
        card.child(heading);

        counts = UIComponents.label(Component.translatable("allthelogs.import.progress.waiting"));
        counts.color(Color.ofRgb(0xA0A0A0));
        counts.maxWidth(Math.max(160, this.width - 96));
        card.child(counts);

        current = UIComponents.label(Component.empty());
        current.color(Color.ofRgb(0x888888));
        current.maxWidth(Math.max(160, this.width - 96));
        card.child(current);

        FlowLayout track = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(10));
        track.surface(Surface.flat(0xFF1A1A1A).and(Surface.outline(0xFF3C3C3C)));
        fill = UIComponents.box(Sizing.fill(1), Sizing.fill());
        fill.fill(true).color(Color.ofRgb(0x7CB342));
        track.child(fill);
        card.child(track);

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        cancel = UIComponents.button(Component.translatable("allthelogs.import.progress.cancel"),
            button -> cancelImport());
        done = UIComponents.button(Component.translatable("allthelogs.done"),
            button -> Minecraft.getInstance().gui.setScreen(parent));
        done.active(false);
        actions.child(cancel);
        actions.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
        actions.child(done);
        card.child(actions);

        root.child(card);
        start();
    }

    private void start() {
        Consumer<ImportProgress> progress = snapshot ->
            Minecraft.getInstance().execute(() -> applyProgress(snapshot));
        CompletableFuture<ImportResult> future = archive
            ? AllTheLogsClient.worker().importArchive(path, options, progress)
            : AllTheLogsClient.worker().importDirectory(path, options, progress);
        future.whenComplete((imported, error) -> Minecraft.getInstance().execute(() -> {
            finished = true;
            done.active(true);
            cancel.active(false);
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("Import failed", error);
                heading.text(Component.translatable("allthelogs.status.import.failed"));
                counts.text(Component.translatable("allthelogs.import.progress.failed",
                    ImportProgressText.failureReason(error)));
                current.text(Component.empty());
                return;
            }
            if (cancelled) {
                heading.text(Component.translatable("allthelogs.import.progress.cancelled",
                    Long.toString(imported.importedFiles()), Long.toString(imported.importedEntries())));
            } else if (imported.importedFiles() == 0 && imported.skippedFiles() > 0) {
                heading.text(Component.translatable("allthelogs.status.imported.skipped",
                    Long.toString(imported.skippedFiles())));
            } else {
                heading.text(Component.translatable("allthelogs.status.imported",
                    Long.toString(imported.importedFiles()), Long.toString(imported.importedEntries())));
            }
            counts.text(Component.translatable("allthelogs.import.progress.done"));
            current.text(Component.empty());
            fill.horizontalSizing(Sizing.fill(100));
        }));
    }

    private void cancelImport() {
        if (finished) return;
        cancelled = true;
        cancel.active(false);
        AllTheLogsClient.worker().cancelImport();
        heading.text(Component.translatable("allthelogs.import.progress.cancelling"));
    }

    private void applyProgress(ImportProgress snapshot) {
        if (finished) return;
        int percent = ImportProgressText.percent(snapshot);
        fill.horizontalSizing(Sizing.fill(Math.max(1, percent)));
        switch (snapshot.phase()) {
            case CHUNKING -> {
                heading.text(Component.translatable("allthelogs.status.chunking"));
                counts.text(Component.translatable("allthelogs.import.progress.chunking", Integer.toString(percent)));
                current.text(Component.empty());
            }
            case OPTIMIZING -> {
                heading.text(Component.translatable("allthelogs.status.optimizing"));
                counts.text(Component.translatable("allthelogs.import.progress.optimizing", Integer.toString(percent)));
                current.text(Component.empty());
            }
            case IMPORT -> {
                if (snapshot.discoveryComplete()) {
                    counts.text(Component.translatable("allthelogs.import.progress.counts",
                        Integer.toString(snapshot.completedFiles()),
                        Integer.toString(Math.max(snapshot.discoveredFiles(), snapshot.estimatedFiles())),
                        Integer.toString(percent)));
                } else {
                    counts.text(Component.translatable("allthelogs.import.progress.discovering",
                        Integer.toString(snapshot.completedFiles()),
                        Integer.toString(Math.max(snapshot.discoveredFiles(), snapshot.estimatedFiles()))));
                }
                String file = ImportProgressText.currentFile(snapshot.current(), path);
                current.text(file.isEmpty()
                    ? Component.empty()
                    : Component.translatable("allthelogs.import.progress.current", file));
            }
        }
    }

    @Override
    public void onClose() {
        if (!finished) {
            AllTheLogsClient.worker().cancelImport();
        }
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
