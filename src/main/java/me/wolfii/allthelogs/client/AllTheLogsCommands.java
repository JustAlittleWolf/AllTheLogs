package me.wolfii.allthelogs.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;

/**
 * Client commands that open each screen: {@code /allthelogs gui}, {@code import}, {@code playtime},
 * {@code cleanup}, {@code scripts}, and {@code settings}.
 */
public final class AllTheLogsCommands {
    private AllTheLogsCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("allthelogs")
            .then(opens("gui", AllTheLogsScreens::openBrowser))
            .then(opens("import", AllTheLogsScreens::openImport))
            .then(opens("playtime", AllTheLogsScreens::openPlaytime))
            .then(opens("cleanup", AllTheLogsScreens::openCleanup))
            .then(opens("scripts", AllTheLogsScreens::openScripts))
            .then(opens("settings", AllTheLogsScreens::openSettings)));
    }

    /**
     * A subcommand that opens a screen with no parent, so closing it returns to the game. The screen opens on
     * the next client task, after the chat screen that ran the command has closed.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> opens(String name, Consumer<Screen> open) {
        return ClientCommands.literal(name).executes(context -> {
            Minecraft.getInstance().execute(() -> open.accept(null));
            return 1;
        });
    }
}
