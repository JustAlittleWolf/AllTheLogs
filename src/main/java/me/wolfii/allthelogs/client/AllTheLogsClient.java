package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.ui.LogBrowserScreen;
import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.store.SessionMarker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class AllTheLogsClient implements ClientModInitializer {
    public static final String MOD_ID = "allthelogs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static LogStoreWorker worker;

    public static LogStoreWorker worker() {
        return worker;
    }

    private static void capture(Component message) {
        worker.importSessionMessage(message.getString());
    }

    private static CompletableFuture<Void> importCurrentLogs() {
        Path logs = AllTheLogsPaths.gameDirectory().resolve("logs");
        if (!Files.isDirectory(logs)) {
            return CompletableFuture.completedFuture(null);
        }
        ImportOptions options = ImportOptions.defaults()
            .withRecursive(false)
            .withNestedArchives(false)
            .withSkipAlreadyImported(true)
            .withPathMatcher("{*.log.gz,*.log}");
        return worker.importDirectory(logs, options, null)
            .thenAccept(result -> LOGGER.info(
                "Imported instance logs: {} files, {} entries ({} skipped)",
                result.importedFiles(), result.importedEntries(), result.skippedFiles()));
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    @Override
    public void onInitializeClient() {
        worker = new LogStoreWorker();

        worker.open(AllTheLogsPaths.database())
            .thenCompose(ignored -> importCurrentLogs())
            .thenCompose(ignored -> worker.startSession(minecraftVersion()))
            .whenComplete((log, error) -> {
                if (error != null) {
                    LOGGER.error("AllTheLogs failed to start", error);
                    return;
                }
                if (log != null && log.source() instanceof LogSource.Session session && session.id() != null) {
                    LOGGER.info(SessionMarker.message(session.id()));
                }
            });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> capture(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) capture(message);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("allthelogs").executes(context -> {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new LogBrowserScreen()));
                return 1;
            })));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> worker.close());
    }
}
