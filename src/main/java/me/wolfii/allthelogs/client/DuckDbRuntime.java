package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.DaemonThreads;
import me.wolfii.allthelogs.client.ui.screen.DuckDbSetupScreen;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbc;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller.Progress;
import me.wolfii.allthelogs.data.duckdb.FabricClassPath;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads the architecture-specific DuckDB native jar before the log store opens.
 */
public final class DuckDbRuntime {
    private static final AtomicReference<Progress> PROGRESS = new AtomicReference<>(new Progress(Progress.Stage.LOADING, 0, 0, DuckDbJdbc.classifier(), null));
    private static final Object LOCK = new Object();
    private static final ExecutorService installerExecutor = Executors.newSingleThreadExecutor(
        runnable -> DaemonThreads.create("allthelogs-duckdb-install", runnable));
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
     * Starts or retries the download. Completes when the native library is on the classpath.
     */
    public static CompletableFuture<Void> ensure() {
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
            PROGRESS.set(new Progress(Progress.Stage.LOADING, 0, 0, DuckDbJdbc.classifier(), null));
            inflight = CompletableFuture.runAsync(() -> {
                try (DuckDbJdbcInstaller installer = installer()) {
                    installer.install(DuckDbRuntime::setProgress);
                } catch (Exception e) {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    setProgress(Progress.failed(message));
                    AllTheLogsClient.LOGGER.error("Failed to load DuckDB JDBC native library", e);
                    throw new CompletionException(e);
                }
            }, installerExecutor);
            return inflight;
        }
    }

    /**
     * Stops a download still in flight so Minecraft can exit.
     */
    public static void shutdown() {
        synchronized (LOCK) {
            if (inflight != null) {
                inflight.cancel(true);
                inflight = null;
            }
            installerExecutor.shutdownNow();
            try {
                installerExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void setProgress(Progress snapshot) {
        PROGRESS.set(snapshot);
        if (snapshot.stage() != Progress.Stage.READY && snapshot.stage() != Progress.Stage.FAILED) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.gui.screen() instanceof DuckDbSetupScreen screen) {
                screen.refresh();
            } else if (snapshot.stage() == Progress.Stage.FAILED && client.gui.overlay() == null && !(client.gui.screen() instanceof DuckDbSetupScreen)) {
                client.gui.setScreen(new DuckDbSetupScreen());
            }
        });
    }

    private static DuckDbJdbcInstaller installer() {
        return new DuckDbJdbcInstaller(DuckDbJdbc.cacheDirectory(), DuckDbJdbc.MAVEN_REPO, new FabricClassPath());
    }
}
