package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.config.AllTheLogsConfig;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.store.SessionMarker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entry: opens the log store, imports this instance's {@code logs} folder plus extra configured
 * directories, captures live {@code [CHAT]} lines from {@code ChatComponent#logChatMessage}, and registers
 * {@code /allthelogs} commands.
 */
public final class AllTheLogsClient implements ClientModInitializer {
    public static final String MOD_ID = "allthelogs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final long SESSION_END_TOUCH_INTERVAL_MS = 60_000L;
    private static final LogStoreBoot boot = new LogStoreBoot();
    private static LogStoreWorker worker;
    private static long lastSessionEndTouchMs;

    public static LogStoreWorker worker() {
        return worker;
    }

    /**
     * Whether startup has finished far enough for the vanilla loading overlay to fade: DuckDB failed
     * to load, or the store is open, boot import (and its optimizations) have run, and the live
     * session has started.
     */
    public static boolean isBootSettled() {
        return boot.isSettled();
    }

    /**
     * Stores a line that Minecraft is about to write as {@code [CHAT]} in {@code latest.log}.
     * Reads the current player and server/world from the client on this call so each live line
     * is stamped with the place in effect then, without polling those values every tick.
     * Called from {@code ChatComponentMixin}; HUD-only chat that skips the logger is never passed in.
     */
    public static void captureLoggedChat(GuiMessage message) {
        if (worker == null || message == null) return;
        Component content = message.content();
        if (content == null) return;
        Minecraft client = Minecraft.getInstance();
        worker.importSessionMessage(content, currentUsername(client), currentServerOrWorld(client));
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    private static String currentUsername() {
        return currentUsername(Minecraft.getInstance());
    }

    private static String currentUsername(Minecraft client) {
        if (client == null) return null;
        var user = client.getUser();
        if (user == null) return null;
        String name = user.getName();
        return name == null || name.isBlank() ? null : name;
    }

    private static String currentServerOrWorld(Minecraft client) {
        if (client == null) return null;
        return ServerId.current(client);
    }

    /**
     * Opens the log store after the DuckDB native library is on the classpath, then imports and
     * starts a session. The loading overlay waits until that pipeline settles.
     */
    public static void onDriverReady() {
        boot.start(
                worker,
                AllTheLogsPaths.database(),
                AllTheLogsPaths.gameDirectory(),
                AllTheLogsConfig.get().extraImportDirectories(),
                minecraftVersion(),
                currentUsername())
            .whenComplete((log, error) -> {
                if (error != null) {
                    LOGGER.error("AllTheLogs failed to start", error);
                    return;
                }
                if (log != null && log.source() instanceof LogSource.Session session && session.id() != null) {
                    LOGGER.info(SessionMarker.message(session.id()));
                }
            });
    }

    @Override
    public void onInitializeClient() {
        worker = new LogStoreWorker();
        AllTheLogsConfig.loadDefault();
        DuckDbRuntime.ensure().whenComplete((ignored, error) -> {
            if (error != null || DuckDbRuntime.hasFailed()) {
                boot.markSettled();
                return;
            }
            onDriverReady();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            AllTheLogsCommands.register(dispatcher));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            if (now - lastSessionEndTouchMs < SESSION_END_TOUCH_INTERVAL_MS) return;
            lastSessionEndTouchMs = now;
            worker.touchSessionEndTime();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> worker.close());
    }
}
