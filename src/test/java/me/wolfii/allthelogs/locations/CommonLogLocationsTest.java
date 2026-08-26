package me.wolfii.allthelogs.locations;

import me.wolfii.allthelogs.AllTheLogs;
import me.wolfii.allthelogs.data.discover.Globs;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        CommonLogLocations.Location vanilla = CommonLogLocations.defaults().getFirst();
        Map<String, String> vars = Map.of(
            "HOME", "/home/jakob",
            "APPDATA", "C:/missing",
            "LOCALAPPDATA", "C:/missing",
            "XDG_DATA_HOME", "/home/jakob/.local/share",
            "USERPROFILE", "/home/jakob"
        );
        Optional<Path> found = vanilla.firstExisting(vars,
            path -> path.toString().contains("Library/Application Support"));
        assertEquals(Path.of("/home/jakob/Library/Application Support/minecraft/logs"), found.orElseThrow());
    }

    @Test
    void includesATemplateForEachNamedLauncher() {
        List<String> ids = CommonLogLocations.defaults().stream().map(CommonLogLocations.Location::id).toList();
        assertEquals(List.of("vanilla", "prism", "lunar", "feather", "labymod", "badlion"), ids);
    }

    @Test
    void rotatedLogMatcherSkipsLatestLog() {
        var pattern = Globs.compile(AllTheLogs.ROTATED_LOGS_MATCHER);
        assertTrue(pattern.matcher("2026-08-26-1.log.gz").matches());
        assertTrue(pattern.matcher("2026-08-26-2.log").matches());
        assertFalse(pattern.matcher("latest.log").matches());
        assertFalse(pattern.matcher("debug.log").matches());
    }
}
