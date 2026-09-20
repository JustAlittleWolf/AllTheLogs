package me.wolfii.allthelogs.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Instance-relative paths: the database, DuckDB JDBC native-jar cache, and GraalJS cache live in {@code .allthelogs}.
 */
public final class AllTheLogsPaths {
    public static final String DATABASE_DIRECTORY = ".allthelogs";
    public static final String DATABASE_FILE_NAME = "logs.duckdb";
    public static final String SCRIPTS_DIRECTORY = "scripts";
    public static final String SCRIPTS_OUTPUT_DIRECTORY = "output";

    private AllTheLogsPaths() {
    }

    public static Path gameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path database() {
        return gameDirectory().resolve(DATABASE_DIRECTORY).resolve(DATABASE_FILE_NAME);
    }

    public static Path scripts() {
        return gameDirectory().resolve(DATABASE_DIRECTORY).resolve(SCRIPTS_DIRECTORY);
    }

    public static Path scriptOutput() {
        return scripts().resolve(SCRIPTS_OUTPUT_DIRECTORY);
    }
}
