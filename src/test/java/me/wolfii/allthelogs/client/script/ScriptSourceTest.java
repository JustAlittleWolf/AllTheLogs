package me.wolfii.allthelogs.client.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptSourceTest {
    @Test
    void transpilesTheIssueSketchIntoCallableJavascript() throws Exception {
        String source = Files.readString(
            Path.of("src/main/resources/me/wolfii/allthelogs/client/script/example.ts"),
            StandardCharsets.UTF_8);
        String js = ScriptSource.toJavaScript(source);
        assertFalse(js.contains("from \"allthelogs\""));
        assertFalse(js.contains("ChatEntry ->"));
        assertTrue(js.contains("allEntries().forEach((entry) =>"));
        assertTrue(js.contains("writeToOutputFile"));
        assertTrue(js.contains("entry.chatLog.minecraftVersion"));
    }

    @Test
    void stripsEsModuleImportsAndParameterTypes() {
        String js = ScriptSource.toJavaScript("""
            import { allEntries, ChatEntry } from "allthelogs";
            allEntries().forEach((entry: ChatEntry) => {
                console.log(entry.message as string);
            });
            """);
        assertFalse(js.contains("import"));
        assertFalse(js.contains(": ChatEntry"));
        assertFalse(js.contains(" as string"));
        assertTrue(js.contains("allEntries().forEach((entry) =>"));
    }
}

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
        assertTrue(first.contains("allEntries()"));
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
