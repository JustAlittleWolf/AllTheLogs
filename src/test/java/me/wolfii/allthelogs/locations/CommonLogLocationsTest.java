package me.wolfii.allthelogs.locations;

import me.wolfii.allthelogs.AllTheLogs;
import me.wolfii.allthelogs.data.discover.Globs;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonLogLocationsTest {
    @Test
    void expandsOsPlaceholders() {
        Map<String, String> vars = Map.of(
            "HOME", "/home/jakob",
            "APPDATA", "C:/Users/jakob/AppData/Roaming",
            "LOCALAPPDATA", "C:/Users/jakob/AppData/Local",
            "XDG_DATA_HOME", "/home/jakob/.local/share",
            "USERPROFILE", "C:/Users/jakob"
        );
        assertEquals("C:/Users/jakob/AppData/Roaming/.minecraft/logs",
            CommonLogLocations.expand("${APPDATA}/.minecraft/logs", vars));
        assertEquals("/home/jakob/.local/share/PrismLauncher/instances",
            CommonLogLocations.expand("${XDG_DATA_HOME}/PrismLauncher/instances", vars));
    }

    @Test
    void firstExistingUsesTheFirstTemplateThatExists() {
        CommonLogLocations.Location location = new CommonLogLocations.Location(
            "example",
            "Example",
            List.of("${APPDATA}/.minecraft/logs", "${HOME}/Library/Application Support/minecraft/logs"),
            AllTheLogs.LOG_FILES_MATCHER,
            false,
            false
        );
        Map<String, String> vars = Map.of(
            "HOME", "/home/jakob",
            "APPDATA", "C:/missing",
            "LOCALAPPDATA", "C:/missing",
            "XDG_DATA_HOME", "/home/jakob/.local/share",
            "USERPROFILE", "/home/jakob"
        );
        Optional<Path> found = location.firstExisting(vars,
            path -> path.toString().contains("Library/Application Support"));
        assertEquals(Path.of("/home/jakob/Library/Application Support/minecraft/logs"), found.orElseThrow());
    }

    @Test
    void defaultsAreEmptyUntilLaunchersAreAdded() {
        assertTrue(CommonLogLocations.defaults().isEmpty());
    }

    @Test
    void logFilesMatcherAcceptsLogAndGzippedLogNames() {
        var pattern = Globs.compile(AllTheLogs.LOG_FILES_MATCHER);
        assertTrue(pattern.matcher("2026-08-26-1.log.gz").matches());
        assertTrue(pattern.matcher("2026-08-26-2.log").matches());
        assertTrue(pattern.matcher("debug.log").matches());
        assertTrue(pattern.matcher("latest.log").matches());
    }
}
