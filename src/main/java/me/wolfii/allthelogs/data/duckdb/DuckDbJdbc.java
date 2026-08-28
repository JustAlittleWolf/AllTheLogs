package me.wolfii.allthelogs.data.duckdb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Maven coordinates and cache layout for the architecture-specific DuckDB JDBC native jar.
 * Cached under {@code ~/.duckdb/jdbc/<version>} so any app on the machine can reuse it.
 */
public final class DuckDbJdbc {
    public static final String VERSION = readVersion();
    public static final String GROUP = "org.duckdb";
    public static final String ARTIFACT = "duckdb_jdbc";
    public static final String MAVEN_REPO = "https://repo1.maven.org/maven2";

    private DuckDbJdbc() {
    }

    public static Path cacheDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path baseCacheDir;

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            baseCacheDir = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData)
                : Path.of(System.getProperty("user.home"), "AppData", "Local");
        } else if (os.contains("mac")) {
            baseCacheDir = Path.of(System.getProperty("user.home"), "Library", "Caches");
        } else {
            String xdgCache = System.getenv("XDG_CACHE_HOME");
            baseCacheDir = xdgCache != null && !xdgCache.isBlank()
                ? Path.of(xdgCache)
                : Path.of(System.getProperty("user.home"), ".cache");
        }

        return baseCacheDir.resolve("duckdb").resolve("jdbc").resolve(VERSION);
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

    private static String readVersion() {
        try (InputStream in = DuckDbJdbc.class.getResourceAsStream("jdbc.properties")) {
            if (in == null) {
                throw new IllegalStateException("missing duckdb jdbc.properties");
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("duckdb jdbc.properties has no version");
            }
            return version.trim();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
