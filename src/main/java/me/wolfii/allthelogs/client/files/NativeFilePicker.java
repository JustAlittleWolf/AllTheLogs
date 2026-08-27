package me.wolfii.allthelogs.client.files;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;
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
        pick(() -> FileDialogCommands.selectFolder(start), onPicked);
    }

    public static void pickArchive(Path initial, Consumer<Path> onPicked) {
        Path start = ImportPaths.startDirectory(initial);
        pick(() -> FileDialogCommands.selectArchive(start, initial), onPicked);
    }

    private static void pick(Supplier<String> dialog, Consumer<Path> onPicked) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            boolean restoreFullscreen = leaveFullscreen(client);
            client.mouseHandler.releaseMouse();
            Thread thread = new Thread(() -> {
                waitForWindowed(client, restoreFullscreen);
                String result;
                try {
                    result = dialog.get();
                } catch (RuntimeException failed) {
                    result = null;
                }
                String chosen = result;
                client.execute(() -> {
                    if (chosen != null && !chosen.isBlank()) {
                        onPicked.accept(Path.of(chosen.strip()));
                    }
                    restoreFullscreen(client, restoreFullscreen);
                });
            }, "allthelogs-file-picker");
            thread.setDaemon(true);
            thread.start();
        });
    }

    static boolean leaveFullscreen(Minecraft client) {
        if (client == null || client.getWindow() == null || !client.getWindow().isFullscreen()) {
            return false;
        }
        client.getWindow().toggleFullScreen();
        client.getWindow().updateFullscreenIfChanged();
        return true;
    }

    static void restoreFullscreen(Minecraft client, boolean restore) {
        if (!restore || client == null || client.getWindow() == null) return;
        if (!client.getWindow().isFullscreen()) {
            client.getWindow().toggleFullScreen();
            client.getWindow().updateFullscreenIfChanged();
        }
    }

    static void waitForWindowed(Minecraft client, boolean leftFullscreen) {
        if (!leftFullscreen) {
            sleep(50);
            return;
        }
        for (int i = 0; i < 40; i++) {
            if (client.getWindow() != null && !client.getWindow().isFullscreen()) break;
            sleep(25);
        }
        sleep(75);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
