package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
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
import me.wolfii.allthelogs.client.script.GraalJsInstaller.Progress;
import me.wolfii.allthelogs.client.script.ScriptFiles;
import me.wolfii.allthelogs.client.script.ScriptFolders;
import me.wolfii.allthelogs.client.script.ScriptHost;
import me.wolfii.allthelogs.client.script.ScriptRuntime;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.client.Minecraft;
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
 * Lists {@code .ts}/{@code .js} scripts, opens the scripts folder, and runs the selected file.
 */
public final class ScriptsScreen extends BaseOwoScreen<FlowLayout> {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "allthelogs-script");
        thread.setDaemon(true);
        return thread;
    });
    private static final int RETRY_THROTTLE_MS = 1000;

    private final Screen parent;
    private LabelComponent engineStatus;
    private LabelComponent console;
    private LabelComponent outputPath;
    private FlowLayout scriptList;
    private ButtonComponent run;
    private @Nullable Path selected;
    private long retryLockoutUntilMs;
    private boolean running;

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

        FlowLayout card = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        card.gap(8)
            .padding(Insets.of(12))
            .surface(PanelSurfaces.card());

        engineStatus = UIComponents.label(engineStatusText());
        engineStatus.color(Color.ofRgb(0xA0A0A0));
        engineStatus.sizing(Sizing.fill(100), Sizing.content());
        card.child(engineStatus);

        scriptList = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        scriptList.gap(4);
        ScrollContainer<FlowLayout> listScroll = UIContainers.verticalScroll(
            Sizing.fill(), Sizing.fixed(90), scriptList);
        listScroll.scrollbar(OverflowScrollbar.vanillaFlat());
        card.child(listScroll);

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

        root.child(card);
        reloadScripts();
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    public void refresh() {
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
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
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
        if (scripts.isEmpty()) {
            LabelComponent empty = UIComponents.label(Component.translatable("allthelogs.scripts.none"));
            empty.color(Color.ofRgb(0xA0A0A0));
            scriptList.child(empty);
            selected = null;
            return;
        }
        if (selected == null || !scripts.contains(selected)) {
            selected = scripts.getFirst();
        }
        for (Path script : scripts) {
            String name = script.getFileName().toString();
            ButtonComponent button = UIComponents.button(Component.literal(name), ignored -> {
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
            case READY -> Component.empty();
            case FAILED -> Component.translatable("allthelogs.scripts.engine.failed",
                progress.error() == null ? "" : progress.error());
            case DOWNLOADING -> Component.translatable("allthelogs.scripts.engine.downloading", progress.percent());
            case VERIFYING -> Component.translatable("allthelogs.scripts.engine.verifying", progress.percent());
            case LOADING -> Component.translatable("allthelogs.scripts.engine.loading");
        };
    }
}
