package me.wolfii.allthelogs.client.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Listing helpers for the in-game import file browser.
 */
final class FileBrowserListing {
    private FileBrowserListing() {
    }

    static boolean isArchive(Path path) {
        Path name = path.getFileName();
        if (name == null) return false;
        String lower = name.toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".tgz")
            || lower.endsWith(".tar.gz") || lower.endsWith(".tar");
    }

    static boolean visible(Path path, boolean archives) {
        if (Files.isDirectory(path)) return true;
        return archives && isArchive(path);
    }

    static List<Path> children(Path directory, boolean archives) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(path -> {
                    Path name = path.getFileName();
                    return name != null && !name.toString().startsWith(".") && visible(path, archives);
                })
                .sorted(Comparator
                    .comparing((Path path) -> !Files.isDirectory(path))
                    .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    static Path startDirectory(Path initial) {
        if (initial != null && Files.isDirectory(initial)) {
            return initial.toAbsolutePath().normalize();
        }
        if (initial != null && initial.getParent() != null) {
            return initial.toAbsolutePath().normalize().getParent();
        }
        return Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
    }
}
