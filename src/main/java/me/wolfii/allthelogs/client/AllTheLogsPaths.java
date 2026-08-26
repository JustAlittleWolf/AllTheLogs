package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.AllTheLogs;

import java.nio.file.Path;

/**
 * Instance-relative paths: the database lives in {@code .allthelogs}, the config in {@code .config}.
 */
public final class AllTheLogsPaths {
    private AllTheLogsPaths() {
    }

    public static Path database(Path gameDirectory) {
        return gameDirectory.resolve(AllTheLogs.DATABASE_DIRECTORY).resolve(AllTheLogs.DATABASE_FILE_NAME);
    }

    public static Path config(Path gameDirectory) {
        return gameDirectory.resolve(AllTheLogs.CONFIG_DIRECTORY).resolve(AllTheLogs.CONFIG_FILE_NAME);
    }
}
