package me.wolfii.allthelogs.client.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Extra Minecraft {@code logs} folders scanned on boot. The running instance is always imported and
 * never written to the config file.
 */
public final class ExtraImportDirectories {
    private ExtraImportDirectories() {
    }

    /**
     * Stores the {@code logs} folder itself. Instance roots are rewritten to their {@code logs} child.
     * The current instance's logs folder is omitted.
     */
    public static List<String> persisted(List<String> configured, Path instanceDir) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Path instanceLogs = instanceLogs(instanceDir);
        if (configured == null) return List.of();
        for (String raw : configured) {
            if (raw == null || raw.isBlank()) continue;
            Path path = toLogsFolder(Path.of(raw.trim()));
            if (path == null) continue;
            if (instanceLogs != null && path.equals(instanceLogs)) continue;
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

    /**
     * The {@code logs} folder to persist: keep a folder named {@code logs}, otherwise append {@code logs}.
     */
    public static Path toLogsFolder(Path directory) {
        Path path = normalize(directory);
        if (path == null) return null;
        if (isLogsFolderName(path)) return path;
        return path.resolve("logs");
    }

    public static boolean isInstanceDirectory(Path directory, Path instanceDir) {
        Path path = normalize(directory);
        Path instance = normalize(instanceDir);
        if (path == null || instance == null) return false;
        Path logs = instance.resolve("logs");
        return path.equals(instance) || path.equals(normalize(logs));
    }

    public static Path normalize(Path path) {
        if (path == null) return null;
        try {
            return path.toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return path.normalize();
        }
    }

    static boolean isLogsFolderName(Path directory) {
        Path name = directory.getFileName();
        return name != null && name.toString().equalsIgnoreCase("logs");
    }

    private static Path instanceLogs(Path instanceDir) {
        Path instance = normalize(instanceDir);
        return instance == null ? null : normalize(instance.resolve("logs"));
    }
}
