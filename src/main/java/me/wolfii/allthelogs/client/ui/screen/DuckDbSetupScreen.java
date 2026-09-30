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
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbc;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller.Progress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Asks before downloading the DuckDB native jar, then shows that download until the log store opens.
 * Quitting is always available. Escape does not dismiss the screen, because the game cannot search
 * logs without the driver.
 */
public final class DuckDbSetupScreen extends BaseOwoScreen<FlowLayout> {
    private static final int RETRY_THROTTLE_MS = 1000;
    private static final int CARD_WIDTH = 420;
    private static final int BUTTON_WIDTH = 128;

    private FlowLayout card;
    private int cardPixels = CARD_WIDTH;
    private LabelComponent status;
    private LabelComponent detail;
    private BoxComponent progressFill;
    private ButtonComponent primary;
    private DownloadView shown;
    private long retryLockoutUntilMs;
    private boolean storeBootRequested;

    public DuckDbSetupScreen() {
        super(Component.translatable("allthelogs.screen.duckdb"));
    }

    static DownloadView view(Progress.Stage stage) {
        return switch (stage) {
            case IDLE -> DownloadView.PROMPT;
            case FAILED -> DownloadView.FAILED;
            case READY -> DownloadView.OPENING;
            case DOWNLOADING, VERIFYING, LOADING -> DownloadView.WORKING;
        };
    }

    /**
     * Fill used by the progress bar. A download with no byte count yet still shows a sliver so the
     * bar is visible, and a failure keeps that sliver instead of an empty track.
     */
    static int barPercent(Progress progress) {
        if (progress == null) return 1;
        if (progress.stage() == Progress.Stage.READY) return 100;
        if (progress.stage() == Progress.Stage.FAILED) return 1;
        return Math.max(1, progress.percent());
    }

    private static Component statusText(Progress progress) {
        return switch (progress.stage()) {
            case DOWNLOADING -> Component.translatable("allthelogs.duckdb.downloading", progress.percent());
            case VERIFYING -> Component.translatable("allthelogs.duckdb.verifying", progress.percent());
            case LOADING -> Component.translatable("allthelogs.duckdb.loading");
            case READY -> Component.translatable("allthelogs.duckdb.opening");
            case FAILED, IDLE -> Component.empty();
        };
    }

    private static String driverFileName() {
        return DuckDbJdbc.jarFileName(DuckDbJdbc.classifier());
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
        show(view(DuckDbRuntime.progress().stage()));
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    public void refresh() {
        if (DuckDbRuntime.isReady()) {
            if (!storeBootRequested) {
                storeBootRequested = true;
                AllTheLogsClient.onDriverReady();
            }
            if (AllTheLogsClient.isStoreBootSettled()) {
                Minecraft.getInstance().gui.setScreen(new TitleScreen());
                return;
            }
        }
        DownloadView next = view(DuckDbRuntime.progress().stage());
        if (next != shown) {
            show(next);
            return;
        }
        updateWorking();
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
        return false;
    }

    @Override
    public void onClose() {
    }

    private void show(DownloadView next) {
        shown = next;
        card.clearChildren();
        status = null;
        detail = null;
        progressFill = null;
        primary = null;
        switch (next) {
            case PROMPT -> showPrompt();
            case WORKING -> showWorking();
            case FAILED -> showFailed();
            case OPENING -> showOpening();
        }
    }

    private void showPrompt() {
        title("allthelogs.duckdb.download.title");
        body(Component.translatable("allthelogs.duckdb.download.body"), Colors.SEARCH_TEXT);
        body(Component.translatable("allthelogs.duckdb.download.file", driverFileName()), Colors.INFO_VERSION);
        rule();
        FlowLayout actions = actions();
        actions.child(button("allthelogs.duckdb.download.start", this::startDownload));
        actions.child(quitButton());
        card.child(actions);
    }

    private void showWorking() {
        title("allthelogs.duckdb.download.title");
        body(Component.translatable("allthelogs.duckdb.download.file", driverFileName()), Colors.INFO_VERSION);
        status = body(statusText(DuckDbRuntime.progress()), Colors.SEARCH_TEXT);
        card.child(progressTrack());
        rule();
        FlowLayout actions = actions();
        actions.child(quitButton());
        card.child(actions);
        updateWorking();
    }

    private void showFailed() {
        title("allthelogs.duckdb.failed");
        body(Component.translatable("allthelogs.duckdb.failed.hint"), Colors.SEARCH_TEXT);
        String error = DuckDbRuntime.progress().error();
        detail = body(Component.translatable("allthelogs.duckdb.failed.detail", error == null ? "" : error),
            Colors.SEARCH_INVALID);
        rule();
        FlowLayout actions = actions();
        primary = button("allthelogs.duckdb.retry", this::startDownload);
        actions.child(primary);
        actions.child(quitButton());
        card.child(actions);
        updateWorking();
    }

    private void showOpening() {
        title("allthelogs.duckdb.opening");
        body(Component.translatable("allthelogs.duckdb.opening.detail"), Colors.SEARCH_TEXT);
        card.child(progressTrack());
        rule();
        FlowLayout actions = actions();
        actions.child(quitButton());
        card.child(actions);
        updateWorking();
    }

    private void title(String key) {
        LabelComponent label = UIComponents.label(Component.translatable(key));
        label.shadow(true);
        label.horizontalTextAlignment(HorizontalAlignment.CENTER);
        label.horizontalSizing(Sizing.fill());
        card.child(label);
    }

    private LabelComponent body(Component text, int rgb) {
        LabelComponent label = UIComponents.label(text);
        label.color(Color.ofRgb(rgb & 0xFFFFFF));
        label.horizontalSizing(Sizing.fill());
        label.maxWidth(Math.max(200, cardWidth() - 40));
        label.lineSpacing(2);
        card.child(label);
        return label;
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

    private ButtonComponent quitButton() {
        return button("allthelogs.duckdb.quit", this::quitGame);
    }

    private int cardWidth() {
        return cardPixels;
    }

    private void updateWorking() {
        Progress progress = DuckDbRuntime.progress();
        if (status != null) {
            status.text(statusText(progress));
        }
        if (detail != null && progress.stage() == Progress.Stage.FAILED) {
            String error = progress.error();
            detail.text(Component.translatable("allthelogs.duckdb.failed.detail", error == null ? "" : error));
        }
        if (progressFill != null) {
            progressFill.horizontalSizing(Sizing.fill(barPercent(progress)));
        }
        if (primary != null) {
            primary.active(System.currentTimeMillis() >= retryLockoutUntilMs);
        }
    }

    private void startDownload() {
        if (System.currentTimeMillis() < retryLockoutUntilMs) return;
        retryLockoutUntilMs = System.currentTimeMillis() + RETRY_THROTTLE_MS;
        if (primary != null) {
            primary.active(false);
        }
        DuckDbRuntime.ensure();
        refresh();
    }

    private void quitGame() {
        Minecraft.getInstance().stop();
    }

    enum DownloadView {
        PROMPT,
        WORKING,
        FAILED,
        OPENING
    }
}
