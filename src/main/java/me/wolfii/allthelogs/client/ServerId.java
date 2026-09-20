package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.extract.ServerOrWorldExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;

/**
 * Stable ids for the server or local world the client is in now.
 * Logic matches HaveIPlayedWith {@code observe.ServerId}: remote servers use the
 * address, local worlds use {@code world/{worldname}}. Formatting is
 * {@link ServerOrWorldExtractor#localWorld} / {@link ServerOrWorldExtractor#remote}
 * so live capture and log import produce the same ids.
 */
public final class ServerId {
    private static final int DEFAULT_PORT = 25565;

    private ServerId() {
    }

    /**
     * Id for the world or server the client is in now, or {@code null} in the menu.
     */
    public static String current(Minecraft client) {
        if (client.hasSingleplayerServer()) {
            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                String name = worldName(server);
                if (name != null) {
                    return localWorld(name);
                }
            }
        }
        String remote = remoteFrom(client.getCurrentServer());
        if (remote != null) {
            return remote;
        }
        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            remote = remoteFrom(connection.getServerData());
            if (remote != null) {
                return remote;
            }
            return fromConnection(connection);
        }
        return null;
    }

    /**
     * Stable id for a local world: {@code world/{worldname}}.
     */
    public static String localWorld(String worldName) {
        return ServerOrWorldExtractor.localWorld(worldName);
    }

    /**
     * Stable id for a remote address. Drops {@code :25565}; IPv6 literals keep their brackets.
     *
     * @return the normalised address, or {@code null} when {@code address} is blank
     */
    public static String remote(String address) {
        return ServerOrWorldExtractor.remote(address);
    }

    private static String worldName(IntegratedServer server) {
        WorldData data = server.getWorldData();
        if (data != null) {
            String name = data.getLevelName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path fileName = root == null ? null : root.getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private static String remoteFrom(ServerData remote) {
        if (remote == null) {
            return null;
        }
        String address = addressOf(remote);
        return address == null ? null : remote(address);
    }

    private static String addressOf(ServerData remote) {
        if (remote.ip != null && !remote.ip.isBlank()) {
            return remote.ip;
        }
        if (remote.name != null && !remote.name.isBlank()) {
            return remote.name;
        }
        return null;
    }

    private static String fromConnection(ClientPacketListener connection) {
        SocketAddress address = connection.getConnection().getRemoteAddress();
        if (address instanceof InetSocketAddress inet) {
            String host = inet.getHostString();
            int port = inet.getPort();
            if (host == null || host.isBlank()) {
                return null;
            }
            return port > 0 && port != DEFAULT_PORT
                ? remote(host + ":" + port)
                : remote(host);
        }
        return null;
    }
}
