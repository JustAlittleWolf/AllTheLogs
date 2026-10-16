package me.wolfii.allthelogs.client.ui.screen.browser;

import me.wolfii.allthelogs.client.AllTheLogsClient;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Minecraft versions and servers or worlds in the store, offered by the version and server filter lists.
 * Servers are read from the store the first time they are needed and kept until the next {@link #versions(List)}
 * update, because reading them scans every message.
 */
final class StoreCatalog {
    private List<String> versions = List.of();
    private List<String> serversOrWorlds;

    /**
     * A catalog that reads the versions itself, for screens opened without the messages screen.
     */
    static StoreCatalog load() {
        StoreCatalog catalog = new StoreCatalog();
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) return catalog;
        worker.browserMetadata().whenComplete((metadata, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null && metadata != null) catalog.versions(metadata.minecraftVersions());
        }));
        return catalog;
    }

    List<String> versions() {
        return versions;
    }

    /**
     * Takes the versions from fresh store metadata. The servers are read again on next use, since the store
     * may have changed.
     */
    void versions(List<String> versions) {
        this.versions = versions == null ? List.of() : versions;
        serversOrWorlds = null;
    }

    /**
     * Hands {@code ready} every stored server or world, once they are known.
     */
    void serversOrWorlds(Consumer<List<String>> ready) {
        if (serversOrWorlds != null) {
            ready.accept(serversOrWorlds);
            return;
        }
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) return;
        worker.serversOrWorlds().whenComplete((servers, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null || servers == null) return;
            serversOrWorlds = servers;
            ready.accept(servers);
        }));
    }
}
