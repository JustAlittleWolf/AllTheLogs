package me.wolfii.allthelogs.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Opens the operating system's folder or file dialog on a worker thread so the client thread stays free.
 */
public final class NativeFilePicker {
    private NativeFilePicker() {
    }

    public static void pickFolder(Path initial, Consumer<Path> onPicked) {
        String start = initial == null ? "" : initial.toAbsolutePath().toString();
        pick(() -> TinyFileDialogs.tinyfd_selectFolderDialog("Select a log folder", start), onPicked);
    }

    public static void pickArchive(Path initial, Consumer<Path> onPicked) {
        String start = initial == null ? "" : initial.toAbsolutePath().toString();
        pick(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.zip"))
                    .put(stack.UTF8("*.7z"))
                    .put(stack.UTF8("*.tar"))
                    .put(stack.UTF8("*.tar.gz"))
                    .flip();
                return TinyFileDialogs.tinyfd_openFileDialog(
                    "Select a log archive", start, filters, "Archives (zip, 7z, tar)", false);
            }
        }, onPicked);
    }

    private static void pick(java.util.function.Supplier<String> dialog, Consumer<Path> onPicked) {
        Thread thread = new Thread(() -> {
            String result = dialog.get();
            if (result == null || result.isBlank()) return;
            Path path = Path.of(result);
            Minecraft.getInstance().execute(() -> onPicked.accept(path));
        }, "allthelogs-file-picker");
        thread.setDaemon(true);
        thread.start();
    }
}
