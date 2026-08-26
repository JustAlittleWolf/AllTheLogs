package me.wolfii.allthelogs.runtime;

import me.wolfii.allthelogs.AllTheLogs;
import me.wolfii.allthelogs.data.ImportOptions;

import java.nio.file.Path;

/**
 * Options used to ingest this instance's {@code logs} directory on startup, skipping {@code latest.log}.
 */
public final class CurrentLogsImport {
    private CurrentLogsImport() {
    }

    public static Path logsDirectory(Path gameDirectory) {
        return gameDirectory.resolve("logs");
    }

    public static ImportOptions options() {
        return ImportOptions.defaults()
            .withRecursive(false)
            .withNestedArchives(false)
            .withSkipAlreadyImported(true)
            .withPathMatcher(AllTheLogs.ROTATED_LOGS_MATCHER);
    }
}
