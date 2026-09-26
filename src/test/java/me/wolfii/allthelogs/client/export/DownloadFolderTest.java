package me.wolfii.allthelogs.client.export;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DownloadFolderTest {
    @Test
    void environmentDownloadDirWinsOverUserDirs() {
        Path home = Path.of("/home/player");
        Path resolved = DownloadFolder.resolve(home, "/data/Incoming", """
            XDG_DOWNLOAD_DIR="$HOME/Downloads"
            """);
        assertEquals(Path.of("/data/Incoming"), resolved);
    }

    @Test
    void userDirsExpandHomeAndIgnoreComments() {
        Path home = Path.of("/home/player");
        String file = """
            # XDG_DOWNLOAD_DIR="/tmp/nope"
            XDG_DESKTOP_DIR="$HOME/Desktop"
            XDG_DOWNLOAD_DIR="$HOME/Downloads"
            XDG_DOWNLOAD_DIR="${HOME}/Incoming"
            """;
        assertEquals("\"${HOME}/Incoming\"", DownloadFolder.downloadDir(file));
        assertEquals(home.resolve("Incoming"), DownloadFolder.resolve(home, "  ", file));
    }

    @Test
    void missingDownloadDirFallsBackToTheHomeDownloadsFolder() {
        Path home = Path.of("/home/player");
        assertNull(DownloadFolder.downloadDir("# nothing\nXDG_DOCUMENTS_DIR=\"$HOME/Documents\"\n"));
        assertEquals(home.resolve("Downloads"), DownloadFolder.resolve(home, null, null));
        assertEquals(Path.of("/var/downloads"), DownloadFolder.expand(home, "\"/var/downloads\""));
    }
}
