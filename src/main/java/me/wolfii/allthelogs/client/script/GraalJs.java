package me.wolfii.allthelogs.client.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Maven coordinates and cache layout for the GraalJS engine, downloaded the first time the scripts
 * screen opens so the published mod jar stays small.
 * Cached under {@code <gameDir>/.allthelogs/graaljs/<version>}, next to the log database.
 */
public final class GraalJs {
    public static final String VERSION = readVersion();
    public static final String MAVEN_REPO = "https://repo1.maven.org/maven2";
    public static final String CONTEXT_CLASS = "org.graalvm.polyglot.Context";
    static final List<Artifact> ARTIFACTS = List.of(
        artifact("org.graalvm.polyglot", "polyglot"),
        artifact("org.graalvm.sdk", "collections"),
        artifact("org.graalvm.sdk", "word"),
        artifact("org.graalvm.sdk", "nativeimage"),
        artifact("org.graalvm.sdk", "jniutils"),
        artifact("org.graalvm.truffle", "truffle-api"),
        artifact("org.graalvm.truffle", "truffle-compiler"),
        artifact("org.graalvm.truffle", "truffle-runtime"),
        artifact("org.graalvm.regex", "regex"),
        artifact("org.graalvm.shadowed", "icu4j"),
        artifact("org.graalvm.shadowed", "xz"),
        artifact("org.graalvm.js", "js-language")
    );

    private GraalJs() {
    }

    public static Path cacheDirectory(Path gameDirectory) {
        return gameDirectory.resolve(".allthelogs").resolve("graaljs").resolve(VERSION);
    }

    public static boolean enginePresent() {
        try {
            Class.forName(CONTEXT_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    static String mavenJarUrl(String repository, Artifact artifact) {
        return repository + "/" + artifact.group().replace('.', '/') + "/" + artifact.name()
            + "/" + VERSION + "/" + artifact.jarFileName();
    }

    private static Artifact artifact(String group, String name) {
        return new Artifact(group, name);
    }

    private static String readVersion() {
        try (InputStream in = GraalJs.class.getResourceAsStream("graaljs.properties")) {
            if (in == null) {
                throw new IllegalStateException("missing graaljs.properties");
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("graaljs.properties has no version");
            }
            return version.trim();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public record Artifact(String group, String name) {
        public String jarFileName() {
            return name + "-" + VERSION + ".jar";
        }
    }
}
