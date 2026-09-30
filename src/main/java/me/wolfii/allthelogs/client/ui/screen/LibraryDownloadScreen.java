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
import me.wolfii.allthelogs.client.DuckDbRuntime;
import me.wolfii.allthelogs.client.script.GraalJsInstaller;
import me.wolfii.allthelogs.client.script.ScriptRuntime;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbc;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Download prompt shared by the DuckDB driver and the GraalJS engine. The card, progress bar, and
 * failure actions are the same. Title, explanation, and the decline button are supplied for each library.
 * DuckDB stays here until the log store opens and decline quits the game. GraalJS returns to the
 * screen that opened it, then shows the script editor once the engine is ready.
 */
public final class LibraryDownloadScreen extends BaseOwoScreen<FlowLayout> {
    private static final int RETRY_THROTTLE_MS = 1000;
    private static final int CARD_WIDTH = 420;
    private static final int BUTTON_WIDTH = 128;

    private final Text text;
    private final Supplier<Snapshot> progress;
    private final Runnable startDownload;
    private final Runnable onDecline;
    private final Runnable onReady;
    private final Runnable onFinished;
    private final BooleanSupplier finished;
    private final boolean closeOnEscape;

    private FlowLayout card;
    private int cardPixels = CARD_WIDTH;
    private LabelComponent status;
    private LabelComponent detail;
    private LabelComponent fileLabel;
    private BoxComponent progressFill;
    private ButtonComponent primary;
    private Phase shown;
    private long retryLockoutUntilMs;
    private boolean readyNotified;

    private LibraryDownloadScreen(
        Component screenTitle,
        Text text,
        Supplier<Snapshot> progress,
        Runnable startDownload,
        Runnable onDecline,
        Runnable onReady,
        Runnable onFinished,
        BooleanSupplier finished,
        boolean closeOnEscape
    ) {
        super(screenTitle);
        this.text = text;
        this.progress = progress;
        this.startDownload = startDownload;
        this.onDecline = onDecline;
        this.onReady = onReady;
        this.onFinished = onFinished;
        this.finished = finished;
        this.closeOnEscape = closeOnEscape;
    }

    public static LibraryDownloadScreen duckDb() {
        return new LibraryDownloadScreen(
            Component.translatable("allthelogs.screen.duckdb"),
            new Text(
                Component.translatable("allthelogs.duckdb.download.title"),
                Component.translatable("allthelogs.duckdb.download.body"),
                Component.translatable("allthelogs.duckdb.quit"),
                Component.translatable("allthelogs.duckdb.opening"),
                Component.translatable("allthelogs.duckdb.opening.detail")
            ),
            () -> Snapshot.fromDuck(DuckDbRuntime.progress()),
            DuckDbRuntime::ensure,
            () -> Minecraft.getInstance().stop(),
            AllTheLogsClient::onDriverReady,
            () -> Minecraft.getInstance().gui.setScreen(new TitleScreen()),
            AllTheLogsClient::isStoreBootSettled,
            false
        );
    }

    public static LibraryDownloadScreen scripts(@Nullable Screen parent) {
        return new LibraryDownloadScreen(
            Component.translatable("allthelogs.screen.scripts"),
            new Text(
                Component.translatable("allthelogs.scripts.download.title"),
                Component.translatable("allthelogs.scripts.download.body"),
                Component.translatable("allthelogs.done"),
                null,
                null
            ),
            () -> Snapshot.fromGraal(ScriptRuntime.progress()),
            ScriptRuntime::ensure,
            () -> Minecraft.getInstance().gui.setScreen(parent),
            () -> {
            },
            () -> Minecraft.getInstance().gui.setScreen(new ScriptsScreen(parent)),
            () -> true,
            true
        );
    }

    static Phase phase(Snapshot.Stage stage, boolean openingAfterReady) {
        return switch (stage) {
            case IDLE -> Phase.PROMPT;
            case FAILED -> Phase.FAILED;
            case READY -> openingAfterReady ? Phase.OPENING : Phase.FINISHED;
            case DOWNLOADING, VERIFYING, LOADING -> Phase.WORKING;
        };
    }

    /**
     * Fill used by the progress bar. A download with no byte count yet still shows a sliver so the
     * bar is visible, and a failure keeps that sliver instead of an empty track.
     */
    static int barPercent(@Nullable Snapshot snapshot) {
        if (snapshot == null) return 1;
        if (snapshot.stage() == Snapshot.Stage.READY) return 100;
        if (snapshot.stage() == Snapshot.Stage.FAILED) return 1;
        return Math.max(1, snapshot.percent());
    }

    private static Component statusText(Snapshot snapshot) {
        return switch (snapshot.stage()) {
            case DOWNLOADING -> Component.translatable("allthelogs.download.downloading", snapshot.percent());
            case VERIFYING -> Component.translatable("allthelogs.download.verifying", snapshot.percent());
            case LOADING -> Component.translatable("allthelogs.download.loading");
            case READY, FAILED, IDLE -> Component.empty();
        };
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(12);
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(24))
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER);

        cardPixels = Math.max(280, Math.min(CARD_WIDTH, Math.max(280, this.width - 48)));
        card = UIContainers.verticalFlow(Sizing.fixed(cardPixels), Sizing.content());
        card.gap(10)
            .padding(Insets.of(20))
            .surface(PanelSurfaces.card())
            .horizontalAlignment(HorizontalAlignment.LEFT);
        root.child(card);
        show(phase(progress.get().stage(), text.opensAfterDownload()));
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    public void refresh() {
        Snapshot snapshot = progress.get();
        if (snapshot.stage() == Snapshot.Stage.READY) {
            if (!readyNotified) {
                readyNotified = true;
                onReady.run();
            }
            if (!text.opensAfterDownload() || finished.getAsBoolean()) {
                onFinished.run();
                return;
            }
        }
        Phase next = phase(snapshot.stage(), text.opensAfterDownload());
        if (next != shown) {
            show(next);
            return;
        }
        updateWorking(snapshot);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) {
            return;
        }
        this.extractPanorama(graphics, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return closeOnEscape;
    }

    @Override
    public void onClose() {
        if (closeOnEscape) {
            onDecline.run();
        }
    }

    private void show(Phase next) {
        shown = next;
        card.clearChildren();
        status = null;
        detail = null;
        fileLabel = null;
        progressFill = null;
        primary = null;
        Snapshot snapshot = progress.get();
        switch (next) {
            case PROMPT -> showPrompt(snapshot);
            case WORKING -> showWorking(snapshot);
            case FAILED -> showFailed(snapshot);
            case OPENING -> showOpening();
            case FINISHED -> {
            }
        }
    }

    private void showPrompt(Snapshot snapshot) {
        title(text.title());
        body(text.body(), Colors.SEARCH_TEXT);
        fileLine(snapshot);
        rule();
        FlowLayout actions = actions();
        actions.child(button("allthelogs.download.start", this::beginDownload));
        actions.child(declineButton());
        card.child(actions);
    }

    private void showWorking(Snapshot snapshot) {
        title(text.title());
        fileLine(snapshot);
        status = body(statusText(snapshot), Colors.SEARCH_TEXT);
        card.child(progressTrack());
        rule();
        FlowLayout actions = actions();
        actions.child(declineButton());
        card.child(actions);
        updateWorking(snapshot);
    }

    private void showFailed(Snapshot snapshot) {
        title(Component.translatable("allthelogs.download.failed"));
        body(Component.translatable("allthelogs.download.failed.hint"), Colors.SEARCH_TEXT);
        String error = snapshot.error();
        detail = body(Component.translatable("allthelogs.download.failed.detail", error == null ? "" : error),
            Colors.SEARCH_INVALID);
        rule();
        FlowLayout actions = actions();
        primary = button("allthelogs.download.retry", this::beginDownload);
        actions.child(primary);
        actions.child(declineButton());
        card.child(actions);
        updateWorking(snapshot);
    }

    private void showOpening() {
        title(text.openingTitle());
        body(text.openingDetail(), Colors.SEARCH_TEXT);
        card.child(progressTrack());
        rule();
        FlowLayout actions = actions();
        actions.child(declineButton());
        card.child(actions);
        updateWorking(progress.get());
    }

    private void title(Component title) {
        LabelComponent label = UIComponents.label(title);
        label.shadow(true);
        label.horizontalTextAlignment(HorizontalAlignment.CENTER);
        label.horizontalSizing(Sizing.fill());
        card.child(label);
    }

    private LabelComponent body(Component text, int rgb) {
        LabelComponent label = UIComponents.label(text);
        label.color(Color.ofRgb(rgb & 0xFFFFFF));
        label.horizontalSizing(Sizing.fill());
        label.maxWidth(Math.max(200, cardPixels - 40));
        label.lineSpacing(2);
        card.child(label);
        return label;
    }

    private void fileLine(Snapshot snapshot) {
        String file = snapshot.file();
        if (file == null || file.isBlank()) return;
        fileLabel = body(Component.translatable("allthelogs.download.file", file), Colors.INFO_VERSION);
    }

    private void rule() {
        BoxComponent line = UIComponents.box(Sizing.fill(), Sizing.fixed(1));
        line.fill(true).color(Color.ofRgb(0x3C3C3C));
        card.child(line);
    }

    private FlowLayout progressTrack() {
        FlowLayout track = UIContainers.horizontalFlow(Sizing.fill(), Sizing.fixed(12));
        track.surface(Surface.flat(0xFF1A1A1A).and(Surface.outline(0xFF3C3C3C)));
        progressFill = UIComponents.box(Sizing.fill(1), Sizing.fill());
        progressFill.fill(true).color(Color.ofRgb(0x7CB342));
        track.child(progressFill);
        return track;
    }

    private FlowLayout actions() {
        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8)
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER);
        return actions;
    }

    private ButtonComponent button(String key, Runnable action) {
        ButtonComponent button = UIComponents.button(Component.translatable(key), ignored -> action.run());
        button.horizontalSizing(Sizing.fixed(BUTTON_WIDTH));
        return button;
    }

    private ButtonComponent declineButton() {
        ButtonComponent button = UIComponents.button(text.decline(), ignored -> onDecline.run());
        button.horizontalSizing(Sizing.fixed(BUTTON_WIDTH));
        return button;
    }

    private void updateWorking(Snapshot snapshot) {
        if (status != null) {
            status.text(statusText(snapshot));
        }
        if (fileLabel != null) {
            String file = snapshot.file();
            fileLabel.text(file == null || file.isBlank()
                ? Component.empty()
                : Component.translatable("allthelogs.download.file", file));
        }
        if (detail != null && snapshot.stage() == Snapshot.Stage.FAILED) {
            String error = snapshot.error();
            detail.text(Component.translatable("allthelogs.download.failed.detail", error == null ? "" : error));
        }
        if (progressFill != null) {
            progressFill.horizontalSizing(Sizing.fill(barPercent(snapshot)));
        }
        if (primary != null) {
            primary.active(System.currentTimeMillis() >= retryLockoutUntilMs);
        }
    }

    private void beginDownload() {
        if (System.currentTimeMillis() < retryLockoutUntilMs) return;
        retryLockoutUntilMs = System.currentTimeMillis() + RETRY_THROTTLE_MS;
        if (primary != null) {
            primary.active(false);
        }
        startDownload.run();
        refresh();
    }

    record Text(
        Component title,
        Component body,
        Component decline,
        @Nullable Component openingTitle,
        @Nullable Component openingDetail
    ) {
        boolean opensAfterDownload() {
            return openingTitle != null;
        }
    }

    record Snapshot(Stage stage, int percent, @Nullable String error, String file) {
        static Snapshot fromDuck(@Nullable DuckDbJdbcInstaller.Progress progress) {
            if (progress == null) return new Snapshot(Stage.IDLE, 0, null, "");
            return new Snapshot(duckStage(progress.stage()), progress.percent(), progress.error(),
                DuckDbJdbc.jarFileName(DuckDbJdbc.classifier()));
        }

        static Snapshot fromGraal(@Nullable GraalJsInstaller.Progress progress) {
            if (progress == null) return new Snapshot(Stage.IDLE, 0, null, "");
            return new Snapshot(graalStage(progress.stage()), progress.percent(), progress.error(), graalFile(progress));
        }

        private static String graalFile(GraalJsInstaller.Progress progress) {
            return switch (progress.stage()) {
                case DOWNLOADING, VERIFYING, LOADING -> progress.artifact() == null ? "" : progress.artifact();
                case IDLE, READY, FAILED -> "";
            };
        }

        private static Stage duckStage(DuckDbJdbcInstaller.Progress.Stage stage) {
            return switch (stage) {
                case IDLE -> Stage.IDLE;
                case DOWNLOADING -> Stage.DOWNLOADING;
                case VERIFYING -> Stage.VERIFYING;
                case LOADING -> Stage.LOADING;
                case READY -> Stage.READY;
                case FAILED -> Stage.FAILED;
            };
        }

        private static Stage graalStage(GraalJsInstaller.Progress.Stage stage) {
            return switch (stage) {
                case IDLE -> Stage.IDLE;
                case DOWNLOADING -> Stage.DOWNLOADING;
                case VERIFYING -> Stage.VERIFYING;
                case LOADING -> Stage.LOADING;
                case READY -> Stage.READY;
                case FAILED -> Stage.FAILED;
            };
        }

        enum Stage {
            IDLE,
            DOWNLOADING,
            VERIFYING,
            LOADING,
            READY,
            FAILED
        }
    }

    enum Phase {
        PROMPT,
        WORKING,
        FAILED,
        OPENING,
        FINISHED
    }
}
