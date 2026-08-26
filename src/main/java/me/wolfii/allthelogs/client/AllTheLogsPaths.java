package me.wolfii.allthelogs.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Instance-relative paths: the database lives in {@code .allthelogs}, the config in Fabric's config directory.
 */
public final class AllTheLogsPaths {
    public static final String DATABASE_DIRECTORY = ".allthelogs";
    public static final String DATABASE_FILE_NAME = "logs.duckdb";
    public static final String CONFIG_FILE_NAME = "allthelogs.json";

    private AllTheLogsPaths() {
    }

    public static Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path database() {
        return gameDirectory().resolve(DATABASE_DIRECTORY).resolve(DATABASE_FILE_NAME);
    }

    public static Path config() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }
}
