package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.parse.ServerPlaceExtractor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Watches the running client for the current remote server or local world and stores it on the
 * live session log. The world name can appear a few ticks after singleplayer start; later values
 * replace earlier ones.
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
        String place = current(client);
        if (Objects.equals(place, lastPlace)) return;
        lastPlace = place;
        LogStoreWorker worker = AllTheLogsClient.worker();
        if (worker == null) return;
        worker.updateSessionPlace(place);
    }

    /**
     * Server-id style place for the world or server the client is in now, or {@code null} in the menu.
     */
    public static String current(Minecraft client) {
        if (client.hasSingleplayerServer()) {
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                String name = worldName(server);
                if (name != null) return ServerPlaceExtractor.localWorld(name);
            }
        }
        String remote = remoteFrom(client.getCurrentServer());
        if (remote != null) return remote;
        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            remote = remoteFrom(connection.getServerData());
            if (remote != null) return remote;
            return fromConnection(connection);
        }
        return null;
    }

    private static String worldName(IntegratedServer server) {
        WorldData data = server.getWorldData();
        if (data != null) {
            String name = data.getLevelName();
            if (name != null && !name.isBlank()) return name;
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path fileName = root == null ? null : root.getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private static String remoteFrom(ServerData remote) {
        if (remote == null) return null;
        String address = addressOf(remote);
        return address == null ? null : ServerPlaceExtractor.remote(address);
    }

    private static String addressOf(ServerData remote) {
        if (remote.ip != null && !remote.ip.isBlank()) return remote.ip;
        if (remote.name != null && !remote.name.isBlank()) return remote.name;
        return null;
    }

    private static String fromConnection(ClientPacketListener connection) {
        SocketAddress address = connection.getConnection().getRemoteAddress();
        if (address instanceof InetSocketAddress inet) {
            String host = inet.getHostString();
            int port = inet.getPort();
            if (host == null || host.isBlank()) return null;
            return port > 0 && port != ServerPlaceExtractor.DEFAULT_PORT
                ? ServerPlaceExtractor.remote(host + ":" + port)
                : ServerPlaceExtractor.remote(host);
        }
        return null;
    }
}
