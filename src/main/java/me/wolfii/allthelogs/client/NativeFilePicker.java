package me.wolfii.allthelogs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Opens the operating system's folder or file dialog on a worker thread so GLFW can keep pumping
 * events. Linux uses zenity or kdialog in a subprocess (Tiny File Dialogs' in-process GTK path
 * deadlocks with GLFW). macOS uses AppleScript. Windows uses Tiny File Dialogs.
 */
public final class NativeFilePicker {
    private NativeFilePicker() {
    }

    public static void pickFolder(Path initial, Consumer<Path> onPicked) {
        Path start = ImportPaths.startDirectory(initial);
        pick(() -> selectFolder(start), onPicked);
    }

    public static void pickArchive(Path initial, Consumer<Path> onPicked) {
        Path start = ImportPaths.startDirectory(initial);
        pick(() -> selectArchive(start, initial), onPicked);
    }

    private static void pick(Supplier<String> dialog, Consumer<Path> onPicked) {
        Thread thread = new Thread(() -> {
            String result = dialog.get();
            if (result == null || result.isBlank()) return;
            Path path = Path.of(result.strip());
            Minecraft.getInstance().execute(() -> onPicked.accept(path));
        }, "allthelogs-file-picker");
        thread.setDaemon(true);
        thread.start();
    }

    private static String selectFolder(Path start) {
        String title = I18n.get("allthelogs.import.dialog.folder");
        String os = osName();
        if (os.contains("mac")) {
            return run(osascriptFolder(title));
        }
        if (os.contains("linux") || os.contains("unix")) {
            if (onPath("zenity")) return run(zenityFolder(start, title));
            if (onPath("kdialog")) return run(kdialogFolder(start, title));
            return swingFolder(start, title);
        }
        try {
            return tinyFolder(start, title);
        } catch (Throwable ignored) {
            return swingFolder(start, title);
        }
    }

    private static String selectArchive(Path start, Path initial) {
        String title = I18n.get("allthelogs.import.dialog.archive");
        Path suggested = initial != null && Files.isRegularFile(initial) ? initial : start;
        String os = osName();
        if (os.contains("mac")) {
            return run(osascriptArchive(title));
        }
        if (os.contains("linux") || os.contains("unix")) {
            if (onPath("zenity")) return run(zenityArchive(suggested, title));
            if (onPath("kdialog")) return run(kdialogArchive(suggested, title));
            return swingArchive(suggested, title);
        }
        try {
            return tinyArchive(suggested, title);
        } catch (Throwable ignored) {
            return swingArchive(suggested, title);
        }
    }

    static List<String> osascriptFolder(String title) {
        return List.of("osascript", "-e",
            "POSIX path of (choose folder with prompt \"" + escapeAppleScript(title) + "\")");
    }

    static List<String> osascriptArchive(String title) {
        return List.of("osascript", "-e",
            "POSIX path of (choose file with prompt \"" + escapeAppleScript(title)
                + "\" of type {\"zip\", \"7z\", \"tar\", \"tgz\"})");
    }

    static List<String> zenityFolder(Path start, String title) {
        return List.of(
            "zenity", "--file-selection", "--directory",
            "--title=" + title,
            "--filename=" + withTrailingSeparator(start));
    }

    static List<String> zenityArchive(Path start, String title) {
        return List.of(
            "zenity", "--file-selection",
            "--title=" + title,
            "--filename=" + start.toAbsolutePath().normalize(),
            "--file-filter=Archives | *.zip *.7z *.tar *.tgz *.gz",
            "--file-filter=All files | *");
    }

    static List<String> kdialogFolder(Path start, String title) {
        return List.of(
            "kdialog", "--title", title,
            "--getexistingdirectory", start.toAbsolutePath().normalize().toString());
    }

    static List<String> kdialogArchive(Path start, String title) {
        return List.of(
            "kdialog", "--title", title,
            "--getopenfilename",
            start.toAbsolutePath().normalize().toString(),
            "*.zip *.7z *.tar *.tgz *.gz|Archives");
    }

    private static String tinyFolder(Path start, String title) {
        return TinyFileDialogs.tinyfd_selectFolderDialog(title, start.toAbsolutePath().toString());
    }

    private static String tinyArchive(Path start, String title) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.zip"))
                .put(stack.UTF8("*.7z"))
                .put(stack.UTF8("*.tar"))
                .put(stack.UTF8("*.tar.gz"))
                .flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                title, start.toAbsolutePath().toString(), filters, "Archives (zip, 7z, tar)", false);
        }
    }

    private static String swingFolder(Path start, String title) {
        return swingChoose(start, title, JFileChooser.DIRECTORIES_ONLY);
    }

    private static String swingArchive(Path start, String title) {
        return swingChoose(start, title, JFileChooser.FILES_ONLY);
    }

    private static String swingChoose(Path start, String title, int mode) {
        AtomicReference<String> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
                JFileChooser chooser = new JFileChooser(start.toFile());
                chooser.setDialogTitle(title);
                chooser.setFileSelectionMode(mode);
                chooser.setAcceptAllFileFilterUsed(true);
                if (mode == JFileChooser.FILES_ONLY) {
                    chooser.setFileFilter(new FileNameExtensionFilter(
                        "Archives (zip, 7z, tar)", "zip", "7z", "tar", "tgz", "gz"));
                }
                int choice = chooser.showOpenDialog(null);
                if (choice == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    if (file != null) {
                        result.set(file.getAbsolutePath());
                    }
                }
            });
        } catch (Exception ignored) {
            return null;
        }
        return result.get();
    }

    static boolean onPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return false;
        String[] directories = path.split(File.pathSeparator);
        for (String directory : directories) {
            if (directory.isBlank()) continue;
            Path candidate = Path.of(directory, executable);
            if (Files.isExecutable(candidate)) return true;
        }
        return false;
    }

    static String run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .strip();
            int code = process.waitFor();
            if (code != 0 || output.isBlank()) return null;
            return output.lines().reduce((first, second) -> second).orElse(output);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }

    static String withTrailingSeparator(Path directory) {
        String path = directory.toAbsolutePath().normalize().toString();
        if (path.endsWith("/") || path.endsWith("\\")) return path;
        return path + File.separator;
    }

    private static String escapeAppleScript(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
