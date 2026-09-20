package me.wolfii.allthelogs.client.script;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Opens the scripts folder in the OS file manager. Tries {@link Desktop} first, then {@code xdg-open},
 * {@code open}, or {@code explorer}.
 */
public final class ScriptFolders {
    private ScriptFolders() {
    }

    public static CompletableFuture<Void> open(Path directory) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(directory);
                if (openDesktop(directory) || openProcess(directory)) {
                    return;
                }
                throw new IOException("no file manager available for " + directory);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private static boolean openDesktop(Path directory) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return false;
        }
        try {
            desktop.open(directory.toFile());
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean openProcess(Path directory) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            command = List.of("explorer", directory.toAbsolutePath().toString());
        } else if (os.contains("mac")) {
            command = List.of("open", directory.toAbsolutePath().toString());
        } else {
            command = List.of("xdg-open", directory.toAbsolutePath().toString());
        }
        try {
            new ProcessBuilder(command).inheritIO().start();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
