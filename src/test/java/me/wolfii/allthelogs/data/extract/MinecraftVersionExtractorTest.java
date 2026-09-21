package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinecraftVersionExtractorTest {
    @Test
    void fabricBeatsIntegratedServerVersion() {
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        versions.accept("[10:00:05] [Server thread/INFO]: Starting integrated minecraft server version 9.9.9");
        versions.accept("[10:00:00] [main/INFO]: Loading Minecraft 1.20.1 with Fabric Loader 0.17.3");
        assertEquals("1.20.1", versions.version());
    }

    @Test
    void oldFabricLoadingForGameBeatsCompactModsList() {
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        versions.accept("[23:15:40] [main/INFO]: Loading for game Minecraft 1.16.5");
        assertEquals("1.16.5", versions.version());
        versions.accept("[23:15:50] [main/INFO]: [FabricLoader] Loading 3 mods: minecraft@1.21.4, java@8, fabricloader@0.11.3");
        assertEquals("1.16.5", versions.version());
    }

    @Test
    void compactModsListWhenNoOtherVersionLineExists() {
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        versions.accept("[23:15:50] [main/INFO]: [FabricLoader] Loading 3 mods: minecraft@1.16.5, java@8, fabricloader@0.11.3");
        assertEquals("1.16.5", versions.version());
    }

    @Test
    void doesNotTreatVersionTypeAsAVersion() {
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        versions.accept("[22:14:10] [main/INFO]: Completely ignored arguments: [--mixin, mixins.feather.json, --versionType, feather]");
        assertNull(versions.version());
    }

    @Test
    void integratedServerIsALastResortIncludingSnapshotNames() {
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        versions.accept("[21:20:01] [Server thread/INFO]: Starting integrated minecraft server version 26.1 Snapshot 2");
        assertEquals("26.1 Snapshot 2", versions.version());
    }
}
