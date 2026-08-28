package me.wolfii.allthelogs.data.duckdb;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Maven coordinates and cache layout for the architecture-specific DuckDB JDBC native jar.
 * Cached under {@code ~/.duckdb/jdbc/<version>} so any app on the machine can reuse it.
 */
public final class DuckDbJdbc {
    public static final String VERSION = "1.5.5.1";
    public static final String GROUP = "org.duckdb";
    public static final String ARTIFACT = "duckdb_jdbc";
    public static final String MAVEN_REPO = "https://repo1.maven.org/maven2";

    private DuckDbJdbc() {
    }

    public static Path cacheDirectory() {
        return Path.of(System.getProperty("user.home"), ".duckdb", "jdbc", VERSION);
    }

    public static String classifier() {
        return classifier(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    static String classifier(String osName, String osArch) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT).trim();
        String arch = normalizeArch(osArch);
        if (os.startsWith("windows")) {
            return "windows_" + arch;
        }
        if (os.startsWith("mac")) {
            return "macos_universal";
        }
        return "linux_" + arch;
    }

    /**
     * Resource name DuckDB JDBC looks up inside the platform jar.
     */
    public static String nativeLibraryResource() {
        return nativeLibraryResource(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    static String nativeLibraryResource(String osName, String osArch) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT).trim();
        String duckOs;
        String arch;
        if (os.startsWith("windows")) {
            duckOs = "windows";
            arch = normalizeArch(osArch);
        } else if (os.startsWith("mac")) {
            duckOs = "osx";
            arch = "universal";
        } else {
            duckOs = "linux";
            arch = normalizeArch(osArch);
        }
        return "libduckdb_java.so_" + duckOs + "_" + arch;
    }

    public static String jarFileName(String classifier) {
        return ARTIFACT + "-" + VERSION + "-" + classifier + ".jar";
    }

    public static String mavenJarUrl(String repository, String classifier) {
        String slashGroup = GROUP.replace('.', '/');
        return repository + "/" + slashGroup + "/" + ARTIFACT + "/" + VERSION + "/" + jarFileName(classifier);
    }

    private static String normalizeArch(String osArch) {
        String arch = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT).trim();
        return switch (arch) {
            case "x86_64", "amd64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch.replaceAll("[^a-z0-9_\\-.]", "");
        };
    }
}
