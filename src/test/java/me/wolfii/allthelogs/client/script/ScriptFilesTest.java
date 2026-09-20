package me.wolfii.allthelogs.client.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptFilesTest {
    @TempDir
    Path tempDir;

    @Test
    void writesExampleOnceAndListsTsAndJs() throws Exception {
        Path scripts = tempDir.resolve("scripts");
        ScriptFiles.ensureExample(scripts);
        Path example = scripts.resolve("example.ts");
        assertTrue(Files.isRegularFile(example));
        String first = Files.readString(example);
        Files.writeString(example, "changed");
        ScriptFiles.ensureExample(scripts);
        assertEquals("changed", Files.readString(example));

        Files.writeString(scripts.resolve("notes.txt"), "ignore");
        Files.writeString(scripts.resolve("extra.js"), "console.log(1)");
        List<Path> listed = ScriptFiles.list(scripts);
        assertEquals(List.of(scripts.resolve("example.ts"), scripts.resolve("extra.js")), listed);
        assertTrue(first.contains("ChatQuery.all()"));
        assertTrue(first.contains("findEntries"));
    }

    @Test
    void exampleDocumentsTheApiInJsdocAndDoesNotImportLibraries() throws Exception {
        String source = Files.readString(
            Path.of("src/main/resources/me/wolfii/allthelogs/client/script/example.ts"),
            StandardCharsets.UTF_8);
        assertTrue(source.lines().noneMatch(line -> {
            String stripped = line.strip();
            return stripped.startsWith("import ") || stripped.startsWith("from \"");
        }));
        assertTrue(source.contains("```ts"));
        assertTrue(source.contains("interface ChatEntry"));
        assertTrue(source.contains("interface ChatLog"));
        assertTrue(source.contains("serverPlace"));
        assertTrue(source.contains("interface ChatQuery"));
        assertTrue(source.contains("interface LogDatabase"));
        assertTrue(source.contains("interface LogStoreMetadata"));
        assertTrue(source.contains("interface MatchSummary"));
        assertTrue(source.contains("interface MatchDay"));
        assertTrue(source.contains("type LogSource"));
        assertTrue(source.contains("declare function allEntries()"));
        assertTrue(source.contains("declare function writeToOutputFile"));
        assertTrue(source.contains("ChatQuery.all()"));
        assertTrue(source.contains("withRegex"));
        assertTrue(source.contains("withLimit(50)"));
        assertTrue(source.contains("findEntries(query)"));
        assertFalse(source.contains("allEntries().forEach"));
    }
}

class GraalJsTest {
    @Test
    void versionComesFromTheVersionCatalog() throws Exception {
        String toml = Files.readString(Path.of("gradle/libs.versions.toml"));
        var match = java.util.regex.Pattern.compile("^graaljs\\s*=\\s*\"([^\"]+)\"", java.util.regex.Pattern.MULTILINE)
            .matcher(toml);
        assertTrue(match.find(), "graaljs version in libs.versions.toml");
        assertEquals(match.group(1), GraalJs.VERSION);
    }

    @Test
    void cacheLivesUnderInstanceAllTheLogsDir() {
        Path gameDir = Path.of("instance");
        assertEquals(
            gameDir.resolve(".allthelogs").resolve("graaljs").resolve(GraalJs.VERSION),
            GraalJs.cacheDirectory(gameDir));
    }

    @Test
    void mavenUrlMatchesCentralLayout() {
        GraalJs.Artifact polyglot = GraalJs.ARTIFACTS.getFirst();
        assertEquals("org.graalvm.polyglot", polyglot.group());
        assertTrue(GraalJs.mavenJarUrl(GraalJs.MAVEN_REPO, polyglot)
            .endsWith("/org/graalvm/polyglot/polyglot/" + GraalJs.VERSION + "/polyglot-" + GraalJs.VERSION + ".jar"));
    }
}
