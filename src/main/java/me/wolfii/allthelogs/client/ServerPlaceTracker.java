package me.wolfii.allthelogs.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/**
 * Watches the running client for the current remote server or local world and stores it on the
 * live session log. The world name can appear a few ticks after singleplayer start; later values
 * replace earlier ones. Detection uses {@link ServerId#current(Minecraft)}.
 */
public final class ServerPlaceTracker {
    private static String lastPlace;

    private ServerPlaceTracker() {
    }

    /**
     * Registers a client-tick listener that writes the current place when it changes.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ServerPlaceTracker::tick);
    }

    private static void tick(Minecraft client) {
        String place = ServerId.current(client);
        if (Objects.equals(place, lastPlace)) return;
        lastPlace = place;
        LogStoreWorker worker = AllTheLogsClient.worker();
        if (worker == null) return;
        worker.updateSessionPlace(place);
    }
}
