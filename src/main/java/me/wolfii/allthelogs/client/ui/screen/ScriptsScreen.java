package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.api.AllTheLogs;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.script.GraalJs;
import me.wolfii.allthelogs.client.script.GraalJsInstaller.Progress;
import me.wolfii.allthelogs.client.script.ScriptFiles;
import me.wolfii.allthelogs.client.script.ScriptFolders;
import me.wolfii.allthelogs.client.script.ScriptHost;
import me.wolfii.allthelogs.client.script.ScriptRuntime;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists {@code .ts}/{@code .js} scripts after the GraalJS engine is present. First open asks before
 * downloading, then shows a single progress bar until the editor is ready.
 */
public final class ScriptsScreen extends BaseOwoScreen<FlowLayout> {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "allthelogs-script");
        thread.setDaemon(true);
        return thread;
    });
    private static final int RETRY_THROTTLE_MS = 1000;

    private final Screen parent;
    private FlowLayout card;
    private LabelComponent engineStatus;
    private LabelComponent console;
    private LabelComponent outputPath;
    private FlowLayout scriptList;
    private TextBoxComponent search;
    private BoxComponent progressFill;
    private ButtonComponent run;
    private ButtonComponent download;
    private @Nullable Path selected;
    private String query = "";
    private long retryLockoutUntilMs;
    private boolean running;
    private boolean showingEditor;

    public ScriptsScreen(@Nullable Screen parent) {
        super(Component.translatable("allthelogs.screen.scripts"));
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(10);
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(16))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        card = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        card.gap(8)
            .padding(Insets.of(12))
            .surface(PanelSurfaces.card());
        root.child(card);
        showAppropriateCard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!showingEditor && ScriptRuntime.isReady()) {
            showAppropriateCard();
            return;
        }
        refresh();
    }

    public void refresh() {
        if (!showingEditor) {
            refreshSetup();
            return;
        }
        if (engineStatus != null) {
            engineStatus.text(engineStatusText());
        }
        if (run != null) {
            if (ScriptRuntime.hasFailed()) {
                boolean locked = System.currentTimeMillis() < retryLockoutUntilMs;
                run.setMessage(Component.translatable("allthelogs.scripts.retry"));
                run.active(!locked);
            } else {
                run.setMessage(Component.translatable("allthelogs.scripts.run"));
                run.active(ScriptRuntime.isReady() && selected != null && !running);
            }
        }
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
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void showAppropriateCard() {
        if (ScriptRuntime.isReady() || GraalJs.enginePresent()) {
            if (GraalJs.enginePresent() && !ScriptRuntime.isReady()) {
                ScriptRuntime.ensure();
            }
            showEditor();
            return;
        }
        Progress progress = ScriptRuntime.progress();
        if (progress.stage() == Progress.Stage.IDLE) {
            showDownloadPrompt();
        } else {
            showDownloadProgress();
        }
    }

    private void showDownloadPrompt() {
        showingEditor = false;
        card.clearChildren();
        card.horizontalAlignment(HorizontalAlignment.LEFT);
        card.child(UIComponents.label(Component.translatable("allthelogs.scripts.download.title")));
        LabelComponent body = UIComponents.label(Component.translatable("allthelogs.scripts.download.body"));
        body.color(Color.ofRgb(0xA0A0A0));
        body.sizing(Sizing.fill(100), Sizing.content());
        card.child(body);
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        download = UIComponents.button(Component.translatable("allthelogs.scripts.download.start"),
            button -> startDownload());
        actions.child(download);
        actions.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
        actions.child(UIComponents.button(Component.translatable("allthelogs.done"),
            button -> Minecraft.getInstance().gui.setScreen(parent)));
        card.child(actions);
    }

    private void showDownloadProgress() {
        showingEditor = false;
        card.clearChildren();
        engineStatus = UIComponents.label(engineStatusText());
        engineStatus.color(Color.ofRgb(0xA0A0A0));
        engineStatus.sizing(Sizing.fill(100), Sizing.content());
        card.child(engineStatus);
        FlowLayout track = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(10));
        track.surface(Surface.flat(0xFF1A1A1A).and(Surface.outline(0xFF3C3C3C)));
        progressFill = UIComponents.box(Sizing.fill(1), Sizing.fill());
        progressFill.fill(true).color(Color.ofRgb(0x7CB342));
        track.child(progressFill);
        card.child(track);
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
        actions.child(UIComponents.button(Component.translatable("allthelogs.done"),
            button -> Minecraft.getInstance().gui.setScreen(parent)));
        card.child(actions);
        refreshSetup();
    }

    private void showEditor() {
        showingEditor = true;
        card.clearChildren();
        card.gap(8);
        card.verticalAlignment(VerticalAlignment.TOP);

        FlowLayout top = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        top.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        search = UIComponents.textBox(Sizing.fixed(160), query);
        search.setHint(Component.translatable("allthelogs.scripts.search"));
        search.setMaxLength(128);
        search.onChanged().subscribe(text -> {
            query = text;
            reloadScripts();
        });
        top.child(search);

        LabelComponent suggestedLabel = UIComponents.label(Component.translatable("allthelogs.scripts.suggested"));
        suggestedLabel.color(Color.ofRgb(0xA0A0A0));
        top.child(suggestedLabel);

        scriptList = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        scriptList.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        ScrollContainer<FlowLayout> chips = UIContainers.horizontalScroll(
            Sizing.expand(), Sizing.fixed(24), scriptList);
        chips.scrollbar(OverflowScrollbar.vanillaFlat());
        top.child(chips);
        card.child(top);

        outputPath = UIComponents.label(Component.empty());
        outputPath.color(Color.ofRgb(0xA0A0A0));
        outputPath.sizing(Sizing.fill(100), Sizing.content());
        card.child(outputPath);

        console = UIComponents.label(Component.empty());
        console.color(Color.ofRgb(0xCCCCCC));
        console.sizing(Sizing.fill(100), Sizing.content());
        ScrollContainer<LabelComponent> consoleScroll = UIContainers.verticalScroll(
            Sizing.fill(), Sizing.expand(), console);
        consoleScroll.scrollbar(OverflowScrollbar.vanillaFlat());
        card.child(consoleScroll);

        engineStatus = UIComponents.label(engineStatusText());
        engineStatus.color(Color.ofRgb(0xA0A0A0));
        engineStatus.sizing(Sizing.fill(100), Sizing.content());
        card.child(engineStatus);

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        actions.child(UIComponents.button(Component.translatable("allthelogs.scripts.open_folder"),
            button -> ScriptFolders.open(AllTheLogsPaths.scripts())));
        run = UIComponents.button(Component.translatable("allthelogs.scripts.run"), button -> primaryAction());
        actions.child(run);
        actions.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
        actions.child(UIComponents.button(Component.translatable("allthelogs.done"),
            button -> Minecraft.getInstance().gui.setScreen(parent)));
        card.child(actions);

        reloadScripts();
        refresh();
    }

    private void startDownload() {
        showDownloadProgress();
        ScriptRuntime.ensure();
    }

    private void refreshSetup() {
        if (engineStatus != null) {
            engineStatus.text(engineStatusText());
        }
        if (progressFill != null) {
            int percent = ScriptRuntime.progress().percent();
            if (ScriptRuntime.hasFailed()) percent = 1;
            else percent = Math.max(1, percent);
            progressFill.horizontalSizing(Sizing.fill(percent));
        }
        if (ScriptRuntime.hasFailed() && download != null) {
            download.setMessage(Component.translatable("allthelogs.scripts.retry"));
            download.active(System.currentTimeMillis() >= retryLockoutUntilMs);
        }
    }

    private void primaryAction() {
        if (ScriptRuntime.hasFailed()) {
            retryEngine();
        } else {
            runSelected();
        }
    }

    private void retryEngine() {
        retryLockoutUntilMs = System.currentTimeMillis() + RETRY_THROTTLE_MS;
        if (run != null) {
            run.active(false);
        }
        ScriptRuntime.ensure();
    }

    private void reloadScripts() {
        if (scriptList == null) return;
        List.copyOf(scriptList.children()).forEach(scriptList::removeChild);
        List<Path> scripts = ScriptFiles.list(AllTheLogsPaths.scripts());
        List<Path> visible = scripts.stream()
            .filter(path -> ScriptFiles.suggested(path) || ScriptFiles.nameMatches(path, query))
            .toList();
        if (visible.isEmpty()) {
            LabelComponent empty = UIComponents.label(Component.translatable("allthelogs.scripts.none"));
            empty.color(Color.ofRgb(0xA0A0A0));
            scriptList.child(empty);
            selected = null;
            return;
        }
        if (selected == null || !visible.contains(selected)) {
            selected = visible.stream().filter(ScriptFiles::suggested).findFirst().orElse(visible.getFirst());
        }
        for (Path script : visible) {
            String name = script.getFileName().toString();
            Component label = ScriptFiles.suggested(script)
                ? Component.translatable("allthelogs.scripts.suggested.item", name)
                : Component.literal(name);
            ButtonComponent button = UIComponents.button(label, ignored -> {
                selected = script;
                reloadScripts();
                refresh();
            });
            if (script.equals(selected)) {
                button.active(false);
            }
            scriptList.child(button);
        }
    }

    private void runSelected() {
        if (selected == null || running || !ScriptRuntime.isReady()) return;
        Path script = selected;
        running = true;
        refresh();
        console.text(Component.translatable("allthelogs.scripts.running", script.getFileName().toString()));
        outputPath.text(Component.empty());
        Path outputFile = ScriptFiles.outputFile(AllTheLogsPaths.scriptOutput(), script.getFileName().toString());
        CompletableFuture.supplyAsync(() -> execute(script, outputFile), EXECUTOR)
            .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                running = false;
                if (error != null) {
                    console.text(Component.literal(error.getMessage() == null ? error.toString() : error.getMessage()));
                } else {
                    show(result);
                }
                refresh();
            }));
    }

    private static ScriptHost.Result execute(Path script, Path outputFile) {
        try {
            String source = Files.readString(script, StandardCharsets.UTF_8);
            return ScriptHost.execute(source, script.getFileName().toString(), AllTheLogs.database(), outputFile);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            AllTheLogsClient.LOGGER.error("Failed to run script {}", script, e);
            return new ScriptHost.Result("", null, message);
        }
    }

    private void show(ScriptHost.Result result) {
        String consoleText = result.console() == null ? "" : result.console();
        if (result.error() != null) {
            consoleText = consoleText.isBlank() ? result.error() : consoleText + result.error();
        }
        console.text(Component.literal(consoleText.isBlank() ? " " : consoleText));
        if (result.outputFile() != null) {
            outputPath.text(Component.translatable("allthelogs.scripts.output", result.outputFile().toString()));
        } else {
            outputPath.text(Component.empty());
        }
    }

    private static Component engineStatusText() {
        Progress progress = ScriptRuntime.progress();
        return switch (progress.stage()) {
            case READY, IDLE -> Component.empty();
            case FAILED -> Component.translatable("allthelogs.scripts.engine.failed",
                progress.error() == null ? "" : progress.error());
            case DOWNLOADING -> Component.translatable("allthelogs.scripts.engine.downloading",
                currentDownload(progress), progress.downloads(), progress.percent());
            case VERIFYING -> Component.translatable("allthelogs.scripts.engine.verifying",
                currentDownload(progress), progress.downloads(), progress.percent());
            case LOADING -> Component.translatable("allthelogs.scripts.engine.loading");
        };
    }

    private static int currentDownload(Progress progress) {
        if (progress.downloads() <= 0) return 1;
        return Math.min(progress.downloads(), progress.completed() + 1);
    }
}
