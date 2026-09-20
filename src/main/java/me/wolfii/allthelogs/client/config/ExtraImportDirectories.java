package me.wolfii.allthelogs.client.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Extra Minecraft instance directories scanned on boot. The running instance is always imported and
 * never written to the config file.
 */
public final class ExtraImportDirectories {
    private ExtraImportDirectories() {
    }

    /**
     * Absolute, de-duplicated extra directories, excluding the current instance game dir and its
     * {@code logs} folder.
     */
    public static List<String> persisted(List<String> configured, Path instanceDir) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Path instance = normalize(instanceDir);
        Path instanceLogs = instance == null ? null : instance.resolve("logs");
        if (configured == null) return List.of();
        for (String raw : configured) {
            if (raw == null || raw.isBlank()) continue;
            Path path = normalize(Path.of(raw.trim()));
            if (path == null) continue;
            if (instance != null && (path.equals(instance) || path.equals(instanceLogs))) continue;
            unique.add(path.toString());
        }
        return List.copyOf(unique);
    }

    /**
     * Directories shown on the settings screen: the current instance first, then persisted extras.
     */
    public static List<Path> displayRoots(Path instanceDir, List<String> extra) {
        List<Path> roots = new ArrayList<>();
        if (instanceDir != null) roots.add(instanceDir);
        for (String directory : persisted(extra, instanceDir)) {
            roots.add(Path.of(directory));
        }
        return List.copyOf(roots);
    }

    /**
     * Extra directories that should be imported after the current instance.
     */
    public static List<Path> scanRoots(List<String> extra, Path instanceDir) {
        List<Path> roots = new ArrayList<>();
        for (String directory : persisted(extra, instanceDir)) {
            roots.add(Path.of(directory));
        }
        return List.copyOf(roots);
    }

    public static boolean isInstanceDirectory(Path directory, Path instanceDir) {
        Path path = normalize(directory);
        Path instance = normalize(instanceDir);
        if (path == null || instance == null) return false;
        return path.equals(instance) || path.equals(instance.resolve("logs"));
    }

    static Path normalize(Path path) {
        if (path == null) return null;
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return path.normalize();
        }
    }
}
