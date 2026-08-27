package me.wolfii.allthelogs.client.files;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeFilePickerTest {
    @Test
    void zenityFolderAsksForADirectory() {
        List<String> command = FileDialogCommands.zenityFolder(Path.of("/tmp/logs"), "Choose a log folder");
        assertEquals("zenity", command.getFirst());
        assertTrue(command.contains("--directory"));
        assertTrue(command.contains("--title=Choose a log folder"));
    }

    @Test
    void zenityArchiveFiltersCommonArchiveExtensions() {
        List<String> command = FileDialogCommands.zenityArchive(Path.of("/tmp/backup.zip"), "Choose a log archive");
        assertTrue(command.stream().anyMatch(part -> part.contains("*.zip")));
        assertTrue(command.contains("--file-selection"));
    }

    @Test
    void kdialogAndOsascriptUseTheNativePrompts() {
        assertEquals("kdialog", FileDialogCommands.kdialogFolder(Path.of("/tmp"), "Choose a log folder").getFirst());
        assertEquals("--getopenfilename",
            FileDialogCommands.kdialogArchive(Path.of("/tmp"), "Choose a log archive").get(3));
        assertEquals("osascript", FileDialogCommands.osascriptFolder("Choose a log folder").getFirst());
        assertTrue(FileDialogCommands.osascriptArchive("Choose a log archive").get(2).contains("choose file"));
    }
}
