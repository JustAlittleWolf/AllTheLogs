package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.DuckDbRuntime;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Shown instead of the title screen only when the DuckDB native library could not be loaded.
 */
public final class DuckDbSetupScreen extends BaseOwoScreen<FlowLayout> {
    private static final int RETRY_THROTTLE_MS = 1000;

    private LabelComponent details;
    private ButtonComponent retry;
    private long retryLockoutUntilMs;

    public DuckDbSetupScreen() {
        super(Component.translatable("allthelogs.screen.duckdb"));
    }

    private static Component failureDetail() {
        String error = DuckDbRuntime.progress().error();
        return Component.translatable("allthelogs.duckdb.failed.detail", error == null ? "" : error);
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

        card.child(UIComponents.label(Component.translatable("allthelogs.duckdb.failed")));

        details = UIComponents.label(failureDetail());
        details.color(Color.ofRgb(0xA0A0A0));
        details.sizing(Sizing.fill(100), Sizing.content());
        card.child(details);

        FlowLayout actions = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        actions.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        retry = UIComponents.button(Component.translatable("allthelogs.duckdb.retry"), button -> retry());
        retry.active(DuckDbRuntime.hasFailed() && System.currentTimeMillis() >= retryLockoutUntilMs);
        actions.child(retry);
        actions.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
        actions.child(UIComponents.button(Component.translatable("allthelogs.duckdb.quit"),
            button -> Minecraft.getInstance().stop()));
        card.child(actions);

        root.child(card);
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    public void refresh() {
        if (DuckDbRuntime.isReady()) {
            AllTheLogsClient.onDriverReady();
            Minecraft.getInstance().gui.setScreen(new TitleScreen());
            return;
        }
        if (details != null) {
            details.text(failureDetail());
        }
        if (retry != null) {
            boolean isLockedOut = System.currentTimeMillis() < retryLockoutUntilMs;
            retry.active(DuckDbRuntime.hasFailed() && !isLockedOut);
        }
    }

    private void retry() {
        retryLockoutUntilMs = System.currentTimeMillis() + DuckDbSetupScreen.RETRY_THROTTLE_MS;
        if (retry != null) {
            retry.active(false);
        }
        DuckDbRuntime.ensure();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
    }
}
