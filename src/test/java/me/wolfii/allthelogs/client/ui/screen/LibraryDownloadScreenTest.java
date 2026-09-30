package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.client.script.GraalJsInstaller;
import me.wolfii.allthelogs.client.ui.screen.LibraryDownloadScreen.Phase;
import me.wolfii.allthelogs.client.ui.screen.LibraryDownloadScreen.Snapshot;
import me.wolfii.allthelogs.data.duckdb.DuckDbJdbcInstaller;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        Snapshot downloading = Snapshot.fromGraal(new GraalJsInstaller.Progress(
            GraalJsInstaller.Progress.Stage.DOWNLOADING, 1, 2, 0, 100, "js-language", null));
        assertEquals(Snapshot.Stage.DOWNLOADING, downloading.stage());
        assertEquals(50, downloading.percent());

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

    @Test
    void aboutLineNamesTheLibraryAndOpensItsPage() {
        assertEquals("https://duckdb.org", LibraryDownloadScreen.DUCKDB_ABOUT_URL);
        assertEquals("https://www.graalvm.org/javascript/", LibraryDownloadScreen.GRAALJS_ABOUT_URL);
        assertAbout(LibraryDownloadScreen.DUCKDB_ABOUT_KEY, LibraryDownloadScreen.DUCKDB_ABOUT_URL);
        assertAbout(LibraryDownloadScreen.GRAALJS_ABOUT_KEY, LibraryDownloadScreen.GRAALJS_ABOUT_URL);
    }

    private static void assertAbout(String key, String url) {
        Component line = LibraryDownloadScreen.aboutLink(key, url);
        assertInstanceOf(TranslatableContents.class, line.getContents());
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEquals(key, contents.getKey());
        assertEquals(1, contents.getArgs().length);
        assertInstanceOf(Component.class, contents.getArgs()[0]);
        Component link = (Component) contents.getArgs()[0];
        assertEquals(url, link.getString());
        assertTrue(link.getStyle().isUnderlined());
        assertInstanceOf(ClickEvent.OpenUrl.class, link.getStyle().getClickEvent());
        assertEquals(URI.create(url), ((ClickEvent.OpenUrl) link.getStyle().getClickEvent()).uri());
    }
}
