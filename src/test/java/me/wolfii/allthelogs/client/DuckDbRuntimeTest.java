package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller.Progress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuckDbRuntimeTest {
    @Test
    void downloadWaitsOnlyWhenTheNativeLibraryIsMissingAndNothingHasStarted() {
        Progress idle = Progress.idle();
        assertFalse(DuckDbRuntime.awaitsDownload(idle, true));
        assertTrue(DuckDbRuntime.awaitsDownload(idle, false));
        assertFalse(DuckDbRuntime.awaitsDownload(Progress.ready(), false));
        assertFalse(DuckDbRuntime.awaitsDownload(Progress.failed("offline"), false));
        assertFalse(DuckDbRuntime.awaitsDownload(
            new Progress(Progress.Stage.LOADING, 0, 0, "linux_amd64", null), false));
        assertFalse(DuckDbRuntime.awaitsDownload(null, false));
    }
}
