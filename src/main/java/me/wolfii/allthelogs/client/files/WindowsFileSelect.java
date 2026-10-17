package me.wolfii.allthelogs.client.files;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Selects a file in Explorer through {@code SHOpenFolderAndSelectItems}. The {@code explorer /select,}
 * command line opens Documents when Java quotes the path, so the shell call is used instead.
 */
final class WindowsFileSelect {
    private static final int COINIT_APARTMENTTHREADED = 2;
    /** The thread is already in a COM apartment of the other kind. The shell call can still run. */
    private static final int RPC_E_CHANGED_MODE = 0x80010106;

    private WindowsFileSelect() {
    }

    static void select(Path file) throws IOException {
        int apartment = initializeApartment();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment filePidl = itemIdList(arena, file.toString());
            try {
                int result = (int) call(() -> Native.SELECT.invokeExact(filePidl, 0, MemorySegment.NULL, 0));
                if (result < 0) {
                    throw new IOException("SHOpenFolderAndSelectItems failed: 0x" + Integer.toHexString(result));
                }
            } finally {
                free(filePidl);
            }
        } finally {
            if (apartment >= 0) uninitializeApartment();
        }
    }

    private static int initializeApartment() throws IOException {
        int result = (int) call(() -> Native.INITIALIZE.invokeExact(MemorySegment.NULL, COINIT_APARTMENTTHREADED));
        if (result != RPC_E_CHANGED_MODE && result < 0) {
            throw new IOException("CoInitializeEx failed: 0x" + Integer.toHexString(result));
        }
        return result;
    }

    private static void uninitializeApartment() {
        try {
            call(() -> {
                Native.UNINITIALIZE.invokeExact();
                return null;
            });
        } catch (IOException ignored) {
            // The file is already selected. Failing to leave the COM apartment should not hide that.
        }
    }

    private static MemorySegment itemIdList(Arena arena, String path) throws IOException {
        MemorySegment wide = arena.allocateFrom(path, StandardCharsets.UTF_16LE);
        MemorySegment item = (MemorySegment) call(() -> Native.CREATE.invokeExact(wide));
        if (item.address() == 0) throw new IOException("ILCreateFromPathW failed for " + path);
        return item;
    }

    private static void free(MemorySegment item) throws IOException {
        if (item != null && item.address() != 0) {
            call(() -> {
                Native.FREE.invokeExact(item);
                return null;
            });
        }
    }

    private static Object call(Call call) throws IOException {
        try {
            return call.run();
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    @FunctionalInterface
    private interface Call {
        Object run() throws Throwable;
    }

    private static final class Native {
        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup SHELL32 = dll("shell32.dll");
        private static final SymbolLookup OLE32 = dll("ole32.dll");
        private static final MethodHandle INITIALIZE = downcall(OLE32, "CoInitializeEx",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        private static final MethodHandle UNINITIALIZE = downcall(OLE32, "CoUninitialize", FunctionDescriptor.ofVoid());
        private static final MethodHandle CREATE = downcall(SHELL32, "ILCreateFromPathW",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        private static final MethodHandle FREE = downcall(SHELL32, "ILFree", FunctionDescriptor.ofVoid(ADDRESS));
        private static final MethodHandle SELECT = downcall(SHELL32, "SHOpenFolderAndSelectItems",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private static SymbolLookup dll(String name) {
            String root = System.getenv("SystemRoot");
            Path dll = Path.of(root == null ? "C:\\Windows" : root, "System32", name);
            return SymbolLookup.libraryLookup(dll, Arena.global());
        }

        private static MethodHandle downcall(SymbolLookup library, String name, FunctionDescriptor descriptor) {
            return LINKER.downcallHandle(library.findOrThrow(name), descriptor);
        }
    }
}
