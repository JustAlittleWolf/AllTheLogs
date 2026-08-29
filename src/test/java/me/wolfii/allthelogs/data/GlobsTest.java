package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.importer.discover.Globs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlobsTest {
    private static boolean matches(String glob, String path) {
        return Globs.compile(glob).matcher(path).matches();
    }

    @Test
    void doubleStarCrossesDirectoryBoundaries() {
        assertTrue(matches("**/logs/**", "instance/logs/2026-08-25-1.log.gz"));
        assertTrue(matches("**/logs/**", "a/b/c/logs/nested/x.log"));
        assertFalse(matches("**/logs/**", "instance/crash-reports/x.log"));
    }

    @Test
    void leadingDoubleStarAlsoMatchesTopLevelDirectories() {
        assertTrue(matches("**/logs/**", "logs/latest.log"));
    }

    @Test
    void singleStarStopsAtDirectoryBoundaries() {
        assertTrue(matches("logs/*.log", "logs/latest.log"));
        assertFalse(matches("logs/*.log", "logs/nested/latest.log"));
    }

    @Test
    void supportsAlternativesAndCharacterClasses() {
        assertTrue(matches("**/*.{log,log.gz}", "logs/a.log"));
        assertTrue(matches("**/*.{log,log.gz}", "logs/a.log.gz"));
        assertTrue(matches("logs/202[0-9]-*.log", "logs/2026-08-25.log"));
        assertFalse(matches("logs/202[0-9]-*.log", "logs/1999-08-25.log"));
    }

    @Test
    void negatedCharacterClassExcludesMatches() {
        assertFalse(matches("logs/[!f]*.log", "logs/fml-client-1.log"));
        assertTrue(matches("logs/[!f]*.log", "logs/latest.log"));
    }

    @Test
    void gameDirectoryMatcherSelectsLogsFoldersAndArchivesInThem() {
        String glob = ImportOptions.GAME_DIRECTORY_MATCHER;
        assertTrue(matches(glob, "logs/2026-08-26-1.log.gz"));
        assertTrue(matches(glob, "logs/old/2026-01-02-1.log.gz"));
        assertTrue(matches(glob, "instances/pack/logs/debug.log"));
        assertTrue(matches(glob, "logs/backup.zip"));
        assertFalse(matches(glob, "resourcepacks/pack.zip"));
        assertFalse(matches(glob, "backup.zip"));
        assertFalse(matches(glob, "2026-08-26-1.log.gz"));
    }

    @Test
    void rejectsMalformedGlobs() {
        assertThrows(IllegalArgumentException.class, () -> Globs.compile("logs/[abc"));
        assertThrows(IllegalArgumentException.class, () -> Globs.compile("logs/{a,b"));
    }

    @Test
    void braceAlternativesMatchLogAndGzippedLogNames() {
        assertTrue(matches("{*.log.gz,*.log}", "2026-08-26-1.log.gz"));
        assertTrue(matches("{*.log.gz,*.log}", "2026-08-26-2.log"));
        assertTrue(matches("{*.log.gz,*.log}", "debug.log"));
        assertTrue(matches("{*.log.gz,*.log}", "latest.log"));
    }

    @Test
    void logsDirectoryMatcherIncludesNestedFilesAndSkipsOtherFolders() {
        String glob = ImportOptions.LOGS_DIRECTORY_MATCHER;
        assertTrue(matches(glob, "2026-08-26-1.log.gz"));
        assertTrue(matches(glob, "debug.log"));
        assertTrue(matches(glob, "old/2026-01-02-1.log.gz"));
        assertFalse(matches(glob, "notes.txt"));
    }
}
