package me.wolfii.allthelogs.client.script;

import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.AllTheLogsPaths;
import me.wolfii.allthelogs.client.script.GraalJsInstaller.Progress;
import me.wolfii.allthelogs.client.ui.screen.ScriptsScreen;
import me.wolfii.allthelogs.data.duckdb.FabricClassPath;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads GraalJS the first time the scripts screen opens. Users who never open it download nothing.
 */
public final class ScriptRuntime {
    private static final AtomicReference<Progress> PROGRESS = new AtomicReference<>(Progress.idle());
    private static final Object LOCK = new Object();
    private static CompletableFuture<Void> inflight;

    private ScriptRuntime() {
    }

    public static boolean isReady() {
        return PROGRESS.get().stage() == Progress.Stage.READY;
    }

    public static boolean hasFailed() {
        return PROGRESS.get().stage() == Progress.Stage.FAILED;
    }

    public static Progress progress() {
        return PROGRESS.get();
    }

    public static CompletableFuture<Void> ensure() {
        synchronized (LOCK) {
            if (isReady()) {
                return CompletableFuture.completedFuture(null);
            }
            if (GraalJs.enginePresent()) {
                setProgress(Progress.ready());
                return CompletableFuture.completedFuture(null);
            }
            if (inflight != null && !inflight.isDone()) {
                return inflight;
            }
            PROGRESS.set(new Progress(Progress.Stage.LOADING, 0, 0, "graaljs", null));
            inflight = CompletableFuture.runAsync(() -> {
                try {
                    installer().install(ScriptRuntime::setProgress);
                } catch (Exception e) {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    setProgress(Progress.failed(message));
                    AllTheLogsClient.LOGGER.error("Failed to load GraalJS", e);
                    throw new CompletionException(e);
                }
            });
            return inflight;
        }
    }

    private static void setProgress(Progress snapshot) {
        PROGRESS.set(snapshot);
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        client.execute(() -> {
            if (client.gui.screen() instanceof ScriptsScreen screen) {
                screen.refresh();
            }
        });
    }

    private static GraalJsInstaller installer() {
        return new GraalJsInstaller(
            GraalJs.cacheDirectory(AllTheLogsPaths.gameDirectory()),
            GraalJs.MAVEN_REPO,
            new FabricClassPath()::add);
    }
}
