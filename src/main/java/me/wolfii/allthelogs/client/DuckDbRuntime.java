package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.client.ui.screen.DuckDbSetupScreen;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbc;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller.Progress;
import me.wolfii.allthelogs.data.duckdb.FabricClassPath;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads the architecture-specific DuckDB native jar before the log store opens.
 * When the jar is not already on the classpath, nothing is downloaded until the player asks.
 */
public final class DuckDbRuntime {
    private static final AtomicReference<Progress> PROGRESS = new AtomicReference<>(Progress.idle());
    private static final Object LOCK = new Object();
    private static CompletableFuture<Void> inflight;

    private DuckDbRuntime() {
    }

    public static boolean isReady() {
        return PROGRESS.get().stage() == Progress.Stage.READY;
    }

    public static boolean hasFailed() {
        return PROGRESS.get().stage() == Progress.Stage.FAILED;
    }

    public static boolean isSettled() {
        return isReady() || hasFailed();
    }

    public static Progress progress() {
        return PROGRESS.get();
    }

    /**
     * The native jar is not on the classpath yet, and nobody has started the download.
     * The title screen stays hidden until the player chooses to download or quit.
     */
    public static boolean awaitsDownload() {
        return awaitsDownload(PROGRESS.get(), DuckDbJdbcInstaller.nativeLibraryPresent());
    }

    static boolean awaitsDownload(Progress progress, boolean nativeLibraryPresent) {
        if (progress == null || nativeLibraryPresent) return false;
        return progress.stage() == Progress.Stage.IDLE;
    }

    /**
     * The cache already has a jar and a checksum file. They are checked on the loader thread, not here.
     */
    public static boolean cacheFilesPresent() {
        return DuckDbJdbcInstaller.cacheFilesPresent(cacheDirectory());
    }

    /**
     * Starts or retries the download. Completes when the native library is on the classpath.
     */
    public static CompletableFuture<Void> ensure() {
        return ensure(true);
    }

    /**
     * Puts a cached jar on the classpath. Does not download; a missing or bad cache fails and leaves
     * the choice to {@link #ensure()}.
     */
    public static CompletableFuture<Void> loadCached() {
        return ensure(false);
    }

    private static CompletableFuture<Void> ensure(boolean allowDownload) {
        synchronized (LOCK) {
            if (isReady()) {
                return CompletableFuture.completedFuture(null);
            }
            if (DuckDbJdbcInstaller.nativeLibraryPresent()) {
                setProgress(Progress.ready());
                return CompletableFuture.completedFuture(null);
            }
            if (inflight != null && !inflight.isDone()) {
                return inflight;
            }
            if (!allowDownload && !cacheFilesPresent()) {
                return CompletableFuture.completedFuture(null);
            }
            PROGRESS.set(new Progress(Progress.Stage.LOADING, 0, 0, DuckDbJdbc.classifier(), null));
            boolean download = allowDownload;
            inflight = CompletableFuture.runAsync(() -> {
                try {
                    installer().install(DuckDbRuntime::setProgress, download);
                } catch (Exception e) {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    setProgress(Progress.failed(message));
                    AllTheLogsClient.LOGGER.error("Failed to load DuckDB JDBC native library", e);
                    throw new CompletionException(e);
                }
            });
            return inflight;
        }
    }

    private static Path cacheDirectory() {
        return DuckDbJdbc.cacheDirectory(AllTheLogsPaths.gameDirectory());
    }

    private static void setProgress(Progress snapshot) {
        PROGRESS.set(snapshot);
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.gui.screen() instanceof DuckDbSetupScreen screen) {
                screen.refresh();
                return;
            }
            if (snapshot.stage() == Progress.Stage.FAILED && client.gui.overlay() == null) {
                client.gui.setScreen(new DuckDbSetupScreen());
            }
        });
    }

    private static DuckDbJdbcInstaller installer() {
        return new DuckDbJdbcInstaller(
            DuckDbJdbc.cacheDirectory(AllTheLogsPaths.gameDirectory()),
            DuckDbJdbc.MAVEN_REPO,
            new FabricClassPath());
    }
}
