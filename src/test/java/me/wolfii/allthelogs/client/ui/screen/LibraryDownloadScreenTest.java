package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.client.script.GraalJsInstaller;
import me.wolfii.allthelogs.client.ui.screen.LibraryDownloadScreen.Phase;
import me.wolfii.allthelogs.client.ui.screen.LibraryDownloadScreen.Snapshot;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbc;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryDownloadScreenTest {
    @Test
    void duckDbAndGraalShareTheSamePhases() {
        assertEquals(Phase.PROMPT, LibraryDownloadScreen.phase(Snapshot.Stage.IDLE, true));
        assertEquals(Phase.PROMPT, LibraryDownloadScreen.phase(Snapshot.Stage.IDLE, false));
        assertEquals(Phase.WORKING, LibraryDownloadScreen.phase(Snapshot.Stage.DOWNLOADING, true));
        assertEquals(Phase.WORKING, LibraryDownloadScreen.phase(Snapshot.Stage.VERIFYING, false));
        assertEquals(Phase.WORKING, LibraryDownloadScreen.phase(Snapshot.Stage.LOADING, true));
        assertEquals(Phase.FAILED, LibraryDownloadScreen.phase(Snapshot.Stage.FAILED, false));
        assertEquals(Phase.OPENING, LibraryDownloadScreen.phase(Snapshot.Stage.READY, true));
        assertEquals(Phase.FINISHED, LibraryDownloadScreen.phase(Snapshot.Stage.READY, false));
    }

    @Test
    void bothLibrariesMapOntoOneSnapshot() {
        Snapshot duck = Snapshot.fromDuck(DuckDbJdbcInstaller.Progress.idle());
        Snapshot graal = Snapshot.fromGraal(GraalJsInstaller.Progress.idle());
        assertEquals(Snapshot.Stage.IDLE, duck.stage());
        assertEquals(Snapshot.Stage.IDLE, graal.stage());
        assertEquals(DuckDbJdbc.jarFileName(DuckDbJdbc.classifier()), duck.file());
        assertEquals("", graal.file());

        Snapshot downloading = Snapshot.fromGraal(new GraalJsInstaller.Progress(
            GraalJsInstaller.Progress.Stage.DOWNLOADING, 1, 2, 0, 100, "js-language", null));
        assertEquals(Snapshot.Stage.DOWNLOADING, downloading.stage());
        assertEquals(50, downloading.percent());
        assertEquals("js-language", downloading.file());

        Snapshot ready = Snapshot.fromDuck(DuckDbJdbcInstaller.Progress.ready());
        assertEquals(Snapshot.Stage.READY, ready.stage());
        assertEquals(Snapshot.Stage.FAILED, Snapshot.fromGraal(GraalJsInstaller.Progress.failed("offline")).stage());
    }

    @Test
    void theBarStaysVisibleBeforeTheFirstByteAndFillsWhenTheDownloadIsReady() {
        assertEquals(1, LibraryDownloadScreen.barPercent(null));
        assertEquals(1, LibraryDownloadScreen.barPercent(Snapshot.fromDuck(DuckDbJdbcInstaller.Progress.idle())));
        assertEquals(1, LibraryDownloadScreen.barPercent(Snapshot.fromGraal(GraalJsInstaller.Progress.failed("offline"))));
        assertEquals(100, LibraryDownloadScreen.barPercent(Snapshot.fromDuck(DuckDbJdbcInstaller.Progress.ready())));
        assertEquals(40, LibraryDownloadScreen.barPercent(Snapshot.fromDuck(
            new DuckDbJdbcInstaller.Progress(DuckDbJdbcInstaller.Progress.Stage.DOWNLOADING, 40, 100, "linux_amd64", null))));
    }
}
