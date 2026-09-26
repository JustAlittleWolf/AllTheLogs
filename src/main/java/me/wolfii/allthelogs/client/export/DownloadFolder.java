package me.wolfii.allthelogs.client.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The user's downloads directory, where exported message files are written.
 * <p>
 * {@code XDG_DOWNLOAD_DIR} wins when it is set. Otherwise the XDG user-dirs file is read, and if that
 * has no download entry the {@code Downloads} folder under the home directory is used.
 */
public final class DownloadFolder {
    private DownloadFolder() {
    }

    public static Path resolve() {
        Path home = home();
        String env = System.getenv("XDG_DOWNLOAD_DIR");
        String userDirs = null;
        Path file = home.resolve(".config").resolve("user-dirs.dirs");
        if (Files.isRegularFile(file)) {
            try {
                userDirs = Files.readString(file);
            } catch (IOException ignored) {
                userDirs = null;
            }
        }
        return resolve(home, env, userDirs);
    }

    static Path resolve(Path home, String envDownload, String userDirsContent) {
        if (envDownload != null && !envDownload.isBlank()) return expand(home, envDownload);
        String parsed = userDirsContent == null ? null : downloadDir(userDirsContent);
        if (parsed != null) return expand(home, parsed);
        return home.resolve("Downloads");
    }

    /**
     * The {@code XDG_DOWNLOAD_DIR} value from an XDG user-dirs file, or {@code null} when it is absent.
     * Later assignments win, matching how a shell would source the file.
     */
    static String downloadDir(String userDirs) {
        String found = null;
        for (String line : userDirs.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') continue;
            if (trimmed.startsWith("XDG_DOWNLOAD_DIR=")) {
                found = trimmed.substring("XDG_DOWNLOAD_DIR=".length()).trim();
            }
        }
        return found == null || found.isEmpty() ? null : found;
    }

    static Path expand(Path home, String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        trimmed = trimmed.replace("${HOME}", home.toString()).replace("$HOME", home.toString());
        Path path = Path.of(trimmed);
        if (!path.isAbsolute()) path = home.resolve(path);
        return path.normalize();
    }

    private static Path home() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) return Path.of(".");
        return Path.of(home);
    }
}
