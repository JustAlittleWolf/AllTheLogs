package me.wolfii.allthelogs.client;

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
            "HOME", "/home/me",
            "APPDATA", "C:/Users/me/AppData/Roaming",
            "LOCALAPPDATA", "C:/Users/me/AppData/Local",
            "XDG_DATA_HOME", "/home/me/.local/share",
            "USERPROFILE", "C:/Users/me"
        );
        assertEquals("C:/Users/me/AppData/Roaming/.minecraft/logs",
            CommonLogLocations.expand("${APPDATA}/.minecraft/logs", vars));
        assertEquals("/home/me/.local/share/PrismLauncher/instances",
            CommonLogLocations.expand("${XDG_DATA_HOME}/PrismLauncher/instances", vars));
    }

    @Test
    void firstExistingUsesTheFirstTemplateThatExists() {
        CommonLogLocations.Location location = new CommonLogLocations.Location(
            "example",
            "Example",
            List.of("${APPDATA}/.minecraft/logs", "${HOME}/Library/Application Support/minecraft/logs"),
            "{*.log.gz,*.log}",
            false,
            false
        );
        Map<String, String> vars = Map.of(
            "HOME", "/home/me",
            "APPDATA", "C:/missing",
            "LOCALAPPDATA", "C:/missing",
            "XDG_DATA_HOME", "/home/me/.local/share",
            "USERPROFILE", "/home/me"
        );
        Optional<Path> found = location.firstExisting(vars, path -> path.toString().replace('\\', '/').contains("Library/Application Support"));
        assertEquals(Path.of("/home/me/Library/Application Support/minecraft/logs"), found.orElseThrow());
    }

    @Test
    void defaultsAreEmptyUntilLaunchersAreAdded() {
        assertTrue(CommonLogLocations.defaults().isEmpty());
    }
}
