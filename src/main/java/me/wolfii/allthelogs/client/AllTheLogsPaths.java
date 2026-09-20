package me.wolfii.allthelogs.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Instance-relative paths: the database lives in {@code .allthelogs}.
 * The DuckDB JDBC native jar is cached under {@code cache/allthelogs}, not next to the database.
 */
public final class AllTheLogsPaths {
    public static final String DATABASE_DIRECTORY = ".allthelogs";
    public static final String DATABASE_FILE_NAME = "logs.duckdb";

    private AllTheLogsPaths() {
    }

    public static Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path database() {
        return gameDirectory().resolve(DATABASE_DIRECTORY).resolve(DATABASE_FILE_NAME);
    }
}
