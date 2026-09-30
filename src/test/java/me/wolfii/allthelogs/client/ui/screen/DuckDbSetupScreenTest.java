package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.client.ui.screen.DuckDbSetupScreen.DownloadView;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller.Progress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuckDbSetupScreenTest {
    @Test
    void thePromptIsTheIdleDownloadAndFailureAndOpeningAreSeparate() {
        assertEquals(DownloadView.PROMPT, DuckDbSetupScreen.view(Progress.Stage.IDLE));
        assertEquals(DownloadView.WORKING, DuckDbSetupScreen.view(Progress.Stage.DOWNLOADING));
        assertEquals(DownloadView.WORKING, DuckDbSetupScreen.view(Progress.Stage.VERIFYING));
        assertEquals(DownloadView.WORKING, DuckDbSetupScreen.view(Progress.Stage.LOADING));
        assertEquals(DownloadView.FAILED, DuckDbSetupScreen.view(Progress.Stage.FAILED));
        assertEquals(DownloadView.OPENING, DuckDbSetupScreen.view(Progress.Stage.READY));
    }

    @Test
    void theBarStaysVisibleBeforeTheFirstByteAndFillsWhenTheDriverIsReady() {
        assertEquals(1, DuckDbSetupScreen.barPercent(null));
        assertEquals(1, DuckDbSetupScreen.barPercent(Progress.idle()));
        assertEquals(1, DuckDbSetupScreen.barPercent(Progress.failed("offline")));
        assertEquals(100, DuckDbSetupScreen.barPercent(Progress.ready()));
        assertEquals(40, DuckDbSetupScreen.barPercent(
            new Progress(Progress.Stage.DOWNLOADING, 40, 100, "linux_amd64", null)));
    }
}
