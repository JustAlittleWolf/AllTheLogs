package me.wolfii.allthelogs.client.config;

import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.LogStoreWorker;
import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Boot-time import of the current instance, then any extra directories from settings, using the same
 * {@link ImportOptions} as the running game directory.
 */
public final class StartupLogImports {
    private StartupLogImports() {
    }

    public record Target(Path root, ImportOptions options) {
    }

    /**
     * Chooses a logs-folder glob when {@code directory} is itself a {@code logs} folder (the extra-import
     * path users add in settings), or when it contains a {@code logs} child. Other directories keep the
     * game-directory glob.
     */
    public static Optional<Target> targetFor(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return Optional.empty();
        if (isLogsFolder(directory)) {
            return Optional.of(new Target(directory, ImportOptions.currentLogsDirectory()));
        }
        Path logs = directory.resolve("logs");
        if (Files.isDirectory(logs)) {
            return Optional.of(new Target(logs, ImportOptions.currentLogsDirectory()));
        }
        return Optional.of(new Target(directory, ImportOptions.currentGameDirectory()));
    }

    static boolean isLogsFolder(Path directory) {
        Path name = directory.getFileName();
        return name != null && name.toString().equalsIgnoreCase("logs");
    }

    public static CompletableFuture<Void> importOnBoot(LogStoreWorker worker, Path instanceDir,
                                                       List<String> extraDirectories) {
        CompletableFuture<Void> chain = importOne(worker, instanceDir, "Imported instance logs");
        for (Path extra : ExtraImportDirectories.scanRoots(extraDirectories, instanceDir)) {
            Path directory = extra;
            chain = chain.thenCompose(ignored -> importOne(worker, directory, "Imported extra logs from " + directory));
        }
        return chain;
    }

    private static CompletableFuture<Void> importOne(LogStoreWorker worker, Path directory, String message) {
        Optional<Target> target = targetFor(directory);
        if (target.isEmpty()) return CompletableFuture.completedFuture(null);
        return worker.importDirectory(target.get().root(), target.get().options(), null)
            .thenAccept(result -> logResult(message, result))
            .exceptionally(error -> {
                AllTheLogsClient.LOGGER.warn("Startup import from {} failed", directory, error);
                return null;
            });
    }

    private static void logResult(String message, ImportResult result) {
        AllTheLogsClient.LOGGER.info("{}: {} files, {} entries ({} skipped)",
            message, result.importedFiles(), result.importedEntries(), result.skippedFiles());
    }
}
