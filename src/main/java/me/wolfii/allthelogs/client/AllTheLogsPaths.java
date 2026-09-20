package me.wolfii.allthelogs.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Instance-relative paths: the database and the DuckDB JDBC native-jar cache live in {@code .allthelogs}.
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
