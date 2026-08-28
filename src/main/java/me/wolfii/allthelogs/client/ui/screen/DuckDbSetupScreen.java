package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.DuckDbRuntime;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller.Progress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Shown instead of the title screen until the DuckDB native library is available.
 * On failure the user can retry the download or quit.
 */
public final class DuckDbSetupScreen extends BaseOwoScreen<FlowLayout> {
    private LabelComponent heading;
    private LabelComponent details;
    private ButtonComponent retry;
    private Progress lastRendered;

    public DuckDbSetupScreen() {
        super(Component.translatable("allthelogs.screen.duckdb"));
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

        heading = UIComponents.label(Component.translatable("allthelogs.duckdb.loading"));
        card.child(heading);

        details = UIComponents.label(Component.empty());
        details.color(Color.ofRgb(0xA0A0A0));
        details.maxWidth(Math.max(160, this.width - 96));
        card.child(details);

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        retry = UIComponents.button(Component.translatable("allthelogs.duckdb.retry"), button -> retry());
        retry.active(false);
        actions.child(retry);
        actions.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
        actions.child(UIComponents.button(Component.translatable("allthelogs.duckdb.quit"),
            button -> Minecraft.getInstance().stop()));
        card.child(actions);

        root.child(card);
        apply(DuckDbRuntime.progress());
        DuckDbRuntime.ensure().whenComplete((ignored, error) ->
            Minecraft.getInstance().execute(() -> apply(DuckDbRuntime.progress())));
    }

    @Override
    protected void init() {
        super.init();
        apply(DuckDbRuntime.progress());
    }

    public void refresh() {
        apply(DuckDbRuntime.progress());
    }

    private void retry() {
        retry.active(false);
        DuckDbRuntime.ensure().whenComplete((ignored, error) ->
            Minecraft.getInstance().execute(() -> apply(DuckDbRuntime.progress())));
    }

    private void apply(Progress snapshot) {
        if (heading == null || snapshot.equals(lastRendered)) return;
        lastRendered = snapshot;
        switch (snapshot.stage()) {
            case READY -> {
                AllTheLogsClient.onDriverReady();
                Minecraft.getInstance().gui.setScreen(new TitleScreen());
            }
            case FAILED -> {
                heading.text(Component.translatable("allthelogs.duckdb.failed"));
                details.text(Component.translatable("allthelogs.duckdb.failed.detail",
                    snapshot.error() == null ? "" : snapshot.error()));
                retry.active(true);
            }
            case DOWNLOADING -> {
                heading.text(Component.translatable("allthelogs.duckdb.downloading"));
                details.text(Component.translatable("allthelogs.duckdb.downloading.detail",
                    snapshot.classifier(), Integer.toString(snapshot.percent())));
                retry.active(false);
            }
            case VERIFYING -> {
                heading.text(Component.translatable("allthelogs.duckdb.verifying"));
                details.text(Component.translatable("allthelogs.duckdb.verifying.detail",
                    snapshot.classifier()));
                retry.active(false);
            }
            case LOADING -> {
                heading.text(Component.translatable("allthelogs.duckdb.loading"));
                details.text(Component.translatable("allthelogs.duckdb.loading.detail",
                    snapshot.classifier()));
                retry.active(false);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
    }
}
