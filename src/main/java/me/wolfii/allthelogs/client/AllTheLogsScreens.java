package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.ui.screen.ImportScreen;
import me.wolfii.allthelogs.client.ui.screen.LogBrowserScreen;
import me.wolfii.allthelogs.client.ui.widget.IconButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Opens AllTheLogs screens and builds the title/pause menu icon button.
 */
public final class AllTheLogsScreens {
    public static final Identifier LOGS_ICON = Identifier.fromNamespaceAndPath(AllTheLogsClient.MOD_ID, "widget/icons/logs");

    private AllTheLogsScreens() {
    }

    public static IconButtonWidget logsButton(int x, int y, Screen parent) {
        Component message = Component.translatable("allthelogs.button.open");
        IconButtonWidget button = new IconButtonWidget(x, y, 20, 20, message, LOGS_ICON, ignored -> openBrowser(parent));
        button.setTooltip(Tooltip.create(message));
        return button;
    }

    public static void openBrowser(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new LogBrowserScreen(parent));
    }

    public static void openImport(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new ImportScreen(parent));
    }
}
