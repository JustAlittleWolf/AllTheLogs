package me.wolfii.allthelogs.client.files;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Asks the operating system file manager to show a file with that file selected.
 * Windows uses Explorer, macOS uses Finder, and Linux asks the desktop file manager over D-Bus.
 * When that service is missing, Linux opens the folder that contains the file.
 */
public final class FileReveal {
    /** How long to wait for the Linux file manager to accept the select request. */
    private static final long LINUX_SELECT_TIMEOUT_MS = 4_000;

    private FileReveal() {
    }

    /**
     * Shows {@code file} in the file manager. The work happens off the caller thread.
     */
    public static CompletableFuture<Void> reveal(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "");
        return CompletableFuture.runAsync(() -> reveal(os, absolute));
    }

    static void reveal(String osName, Path absolute) {
        try {
            switch (platform(osName)) {
                case WINDOWS -> run(windowsSelect(absolute));
                case MAC -> run(macSelect(absolute));
                case LINUX -> revealOnLinux(absolute);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while showing " + absolute, e);
        }
    }

    /**
     * {@code explorer /select,} takes the path in the same argument. A space after the comma opens
     * the default folder and leaves the file unselected.
     */
    static List<String> windowsSelect(Path file) {
        return List.of("explorer.exe", "/select," + file.toString());
    }

    static List<String> macSelect(Path file) {
        return List.of("open", "-R", file.toString());
    }

    static List<String> linuxSelect(Path file) {
        String uri = file.toUri().toString();
        return List.of(
            "dbus-send",
            "--session",
            "--print-reply",
            "--reply-timeout=2000",
            "--dest=org.freedesktop.FileManager1",
            "--type=method_call",
            "/org/freedesktop/FileManager1",
            "org.freedesktop.FileManager1.ShowItems",
            "array:string:\"" + uri + "\"",
            "string:\"\"");
    }

    static List<String> linuxFolder(Path file) {
        Path parent = file.getParent();
        Path folder = parent == null ? file : parent;
        return List.of("xdg-open", folder.toString());
    }

    private static void revealOnLinux(Path absolute) throws IOException, InterruptedException {
        try {
            if (runAndSucceed(linuxSelect(absolute), LINUX_SELECT_TIMEOUT_MS)) return;
        } catch (IOException ignored) {
            // dbus-send is not installed. Open the containing folder instead.
        }
        run(linuxFolder(absolute));
    }

    private static void run(List<String> command) throws IOException {
        process(command).start();
    }

    private static boolean runAndSucceed(List<String> command, long timeoutMs) throws IOException, InterruptedException {
        Process process = process(command).start();
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return false;
        }
        return process.exitValue() == 0;
    }

    private static ProcessBuilder process(List<String> command) {
        return new ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
    }

    private static Platform platform(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("mac")) return Platform.MAC;
        return Platform.LINUX;
    }

    private enum Platform {
        WINDOWS,
        MAC,
        LINUX
    }
}
