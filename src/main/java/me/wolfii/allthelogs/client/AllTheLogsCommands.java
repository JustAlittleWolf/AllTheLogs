package me.wolfii.allthelogs.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/**
 * Client commands: {@code /allthelogs gui} and {@code /allthelogs import}.
 */
public final class AllTheLogsCommands {
    private AllTheLogsCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("allthelogs")
            .then(ClientCommands.literal("gui").executes(context -> {
                Minecraft.getInstance().execute(() -> AllTheLogsScreens.openBrowser(null));
                return 1;
            }))
            .then(ClientCommands.literal("import").executes(context -> {
                Minecraft.getInstance().execute(() -> AllTheLogsScreens.openImport(null));
                return 1;
            })));
    }
}
