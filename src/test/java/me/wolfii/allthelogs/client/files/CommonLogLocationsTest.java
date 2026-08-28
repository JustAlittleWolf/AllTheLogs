package me.wolfii.allthelogs.client.files;

import me.wolfii.allthelogs.data.ImportOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CommonLogLocationsTest {
    private static final Map<String, String> VARS = Map.of(
        "HOME", "/home/me",
        "APPDATA", "C:/Users/me/AppData/Roaming",
        "LOCALAPPDATA", "C:/Users/me/AppData/Local",
        "XDG_DATA_HOME", "/home/me/.local/share",
        "USERPROFILE", "C:/Users/me"
    );

    private static void assertResolvedContains(String id, String expected) {
        List<String> resolved = byId(id).resolveAll(VARS).stream()
            .map(path -> path.toString().replace('\\', '/'))
            .collect(Collectors.toList());
        assertTrue(resolved.contains(expected), id + " templates " + resolved + " missing " + expected);
    }

    private static CommonLogLocations.Location byId(String id) {
        return CommonLogLocations.defaults().stream()
            .filter(location -> id.equals(location.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing location id " + id));
    }

    @Test
    void expandsOsPlaceholders() {
        assertEquals("C:/Users/me/AppData/Roaming/.minecraft/logs",
            CommonLogLocations.expand("${APPDATA}/.minecraft/logs", VARS));
        assertEquals("/home/me/.local/share/PrismLauncher/instances",
            CommonLogLocations.expand("${XDG_DATA_HOME}/PrismLauncher/instances", VARS));
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
    void defaultsArePopulatedWithUniqueIds() {
        List<CommonLogLocations.Location> defaults = CommonLogLocations.defaults();
        assertFalse(defaults.isEmpty());
        List<String> ids = defaults.stream().map(CommonLogLocations.Location::id).toList();
        Set<String> unique = new HashSet<>(ids);
        assertEquals(ids.size(), unique.size(), "duplicate location ids: " + ids);
        assertTrue(unique.containsAll(List.of("vanilla", "prism", "badlion", "labymod")));
    }

    @Test
    void wellKnownLaunchersExpandWithTheSharedVarMap() {
        assertResolvedContains("vanilla", "C:/Users/me/AppData/Roaming/.minecraft/logs");
        assertResolvedContains("vanilla", "/home/me/.minecraft/logs");
        assertResolvedContains("vanilla", "/home/me/Library/Application Support/minecraft/logs");
        assertResolvedContains("prism", "/home/me/.local/share/PrismLauncher/instances");
        assertResolvedContains("prism", "C:/Users/me/AppData/Roaming/PrismLauncher/instances");
        assertResolvedContains("badlion", "C:/Users/me/AppData/Roaming/.minecraft/logs/blclient/minecraft");
        assertResolvedContains("labymod", "C:/Users/me/AppData/Roaming/.minecraft/logs");
        assertResolvedContains("lunar", "C:/Users/me/.lunarclient/offline");
        assertResolvedContains("modrinth", "C:/Users/me/AppData/Roaming/ModrinthApp/profiles");
        assertResolvedContains("curseforge", "C:/Users/me/curseforge/minecraft/Instances");
        assertResolvedContains("feather", "C:/Users/me/AppData/Roaming/.feather/profiles");
    }

    @Test
    void vanillaAndPrismSuggestMatchingImportOptions() {
        CommonLogLocations.Location vanilla = byId("vanilla");
        assertEquals("{*.log.gz,*.log}", vanilla.pathMatcher());
        assertFalse(vanilla.recursive());
        assertFalse(vanilla.nestedArchives());
        assertEquals("{*.log.gz,*.log}", vanilla.suggestedOptions().pathMatcher());

        CommonLogLocations.Location prism = byId("prism");
        assertEquals(ImportOptions.GAME_DIRECTORY_MATCHER, prism.pathMatcher());
        assertTrue(prism.recursive());
        assertFalse(prism.nestedArchives());
    }

    @Test
    void preferredPathFallsBackToTheFirstTemplate() {
        CommonLogLocations.Location vanilla = byId("vanilla");
        Path preferred = vanilla.preferredPath(VARS, path -> false);
        assertEquals(Path.of("C:/Users/me/AppData/Roaming/.minecraft/logs"), preferred);
    }
}
