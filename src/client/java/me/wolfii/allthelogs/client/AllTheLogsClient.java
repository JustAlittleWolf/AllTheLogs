package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.AllTheLogs;
import me.wolfii.allthelogs.client.ui.LogBrowserScreen;
import me.wolfii.allthelogs.runtime.AllTheLogsSettings;
import me.wolfii.allthelogs.runtime.CurrentLogsImport;
import me.wolfii.allthelogs.runtime.LogStoreWorker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client entrypoint: opens the store, imports this instance's rotated logs, and captures live chat off-thread.
 */
public final class AllTheLogsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(AllTheLogs.MOD_ID);

    private static LogStoreWorker worker;
    private static AllTheLogsSettings settings;
    private static Path gameDirectory;

    public static LogStoreWorker worker() {
        return worker;
    }

    public static AllTheLogsSettings settings() {
        return settings;
    }

    public static Path gameDirectory() {
        return gameDirectory;
    }

    @Override
    public void onInitializeClient() {
        gameDirectory = FabricLoader.getInstance().getGameDir();
        worker = new LogStoreWorker();
        try {
            settings = AllTheLogsSettings.load(AllTheLogsPaths.config(gameDirectory));
        } catch (IOException e) {
            settings = new AllTheLogsSettings();
            LOGGER.warn("Could not load AllTheLogs config from {}", AllTheLogsPaths.config(gameDirectory), e);
        }

        worker.open(AllTheLogsPaths.database(gameDirectory))
            .thenCompose(ignored -> importCurrentLogs())
            .thenCompose(ignored -> worker.startSession(minecraftVersion()))
            .whenComplete((ignored, error) -> {
                if (error != null) {
                    LOGGER.error("AllTheLogs failed to start", error);
                }
            });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) ->
            capture(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) capture(message);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("allthelogs").executes(context -> {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new LogBrowserScreen()));
                return 1;
            })));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            saveSettings();
            worker.close();
        });
    }

    public static void saveSettings() {
        if (settings == null || gameDirectory == null) return;
        try {
            settings.save(AllTheLogsPaths.config(gameDirectory));
        } catch (IOException e) {
            LOGGER.warn("Could not save AllTheLogs config", e);
        }
    }

    public static void openBrowser() {
        Minecraft.getInstance().gui.setScreen(new LogBrowserScreen());
    }

    private static void capture(Component message) {
        worker.importSessionMessage(message.getString());
    }

    private static java.util.concurrent.CompletableFuture<Void> importCurrentLogs() {
        Path logs = CurrentLogsImport.logsDirectory(gameDirectory);
        if (!Files.isDirectory(logs)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return worker.importDirectory(logs, CurrentLogsImport.options(), null)
            .thenAccept(result -> LOGGER.info(
                "Imported instance logs: {} files, {} entries ({} skipped)",
                result.importedFiles(), result.importedEntries(), result.skippedFiles()));
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }
}
