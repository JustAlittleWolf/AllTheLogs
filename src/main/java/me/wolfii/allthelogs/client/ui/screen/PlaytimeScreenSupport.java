package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.playtime.PlaytimeChartModel;
import me.wolfii.allthelogs.client.playtime.PlaytimeDuration;
import me.wolfii.allthelogs.client.playtime.PlaytimeSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Loading, filter menus, and button labels shared by the timeline and weekday playtime screens.
 */
final class PlaytimeScreenSupport {
    private PlaytimeScreenSupport() {
    }

    static void load(Screen screen, int request, IntSupplier token, Consumer<Component> status, Runnable ready) {
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) {
            status.accept(Component.translatable("allthelogs.playtime.unavailable"));
            return;
        }
        if (!PlaytimeSession.loaded()) status.accept(Component.translatable("allthelogs.playtime.loading"));
        worker.playtime().whenComplete((spans, error) -> Minecraft.getInstance().execute(() -> {
            if (request != token.getAsInt() || Minecraft.getInstance().gui.screen() != screen) return;
            if (error != null) {
                AllTheLogsClient.LOGGER.warn("Could not read playtime", error);
                status.accept(Component.translatable("allthelogs.status.error"));
                return;
            }
            PlaytimeSession.spans(spans);
            status.accept(null);
            ready.run();
        }));
    }

    static void showTotal(@Nullable LabelComponent total, @Nullable Consumer<Component> placeholder,
                           @Nullable Component message, long totalMillis) {
        if (total == null) return;
        if (message != null) {
            total.text(message);
            if (placeholder != null) placeholder.accept(message);
            return;
        }
        total.text(Component.translatable("allthelogs.playtime.total", PlaytimeDuration.format(totalMillis)));
        if (placeholder != null) placeholder.accept(Component.translatable("allthelogs.playtime.empty"));
    }

    static void openVersions(ChecklistMenu menu, ButtonComponent button, Runnable closeMenus, Runnable rebuild) {
        boolean open = menu.isOpen();
        closeMenus.run();
        if (open) return;
        List<String> values = PlaytimeChartModel.versionsIn(PlaytimeSession.spans());
        menu.show(button, values, PlaytimeSession.filter().versions(), PlaytimeLegend::named,
            (value, selected) -> {
                PlaytimeSession.filter(PlaytimeSession.filter().toggleVersion(value, selected, values));
                rebuild.run();
            }, () -> {
                PlaytimeSession.filter(PlaytimeSession.filter().withVersions(null));
                menu.close();
                rebuild.run();
            });
    }

    static void openServers(ChecklistMenu menu, ButtonComponent button, Runnable closeMenus, Runnable rebuild) {
        boolean open = menu.isOpen();
        closeMenus.run();
        if (open) return;
        List<String> values = PlaytimeChartModel.serversIn(PlaytimeSession.spans());
        menu.show(button, values, PlaytimeSession.filter().servers(), PlaytimeLegend::named,
            (value, selected) -> {
                PlaytimeSession.filter(PlaytimeSession.filter().toggleServer(value, selected, values));
                rebuild.run();
            }, () -> {
                PlaytimeSession.filter(PlaytimeSession.filter().withServers(null));
                menu.close();
                rebuild.run();
            });
    }

    static Component versionLabel() {
        return PlaytimeLegend.selection("allthelogs.playtime.versions", PlaytimeSession.filter().versions(),
            PlaytimeChartModel.versionsIn(PlaytimeSession.spans()));
    }

    static Component serverLabel() {
        return PlaytimeLegend.selection("allthelogs.playtime.servers", PlaytimeSession.filter().servers(),
            PlaytimeChartModel.serversIn(PlaytimeSession.spans()));
    }

    static ButtonComponent button(String key, Consumer<ButtonComponent> onPress) {
        return button(Component.translatable(key), onPress);
    }

    static ButtonComponent button(Component label, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = UIComponents.button(label, onPress::accept);
        button.horizontalSizing(Sizing.content());
        return button;
    }
}
