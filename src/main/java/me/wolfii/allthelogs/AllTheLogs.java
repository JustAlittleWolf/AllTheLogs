package me.wolfii.allthelogs;

/**
 * Shared constants for the Fabric mod and its data layer.
 */
public final class AllTheLogs {
    public static final String MOD_ID = "allthelogs";
    public static final String DATABASE_DIRECTORY = ".allthelogs";
    public static final String DATABASE_FILE_NAME = "logs.duckdb";
    public static final String CONFIG_DIRECTORY = ".config";
    public static final String CONFIG_FILE_NAME = "allthelogs.json";

    /**
     * Glob relative to a {@code logs} directory that matches chat logs. {@code latest.log} is skipped in discovery
     * because it is the live Minecraft log.
     */
    public static final String LOG_FILES_MATCHER = "{*.log.gz,*.log}";

    private AllTheLogs() {
    }
}
