package me.wolfii.allthelogs.client.script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Appends script lines under {@code .allthelogs/scripts/output}.
 */
final class ScriptOutput {
    private final Path file;
    private boolean written;

    ScriptOutput(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    Path file() {
        return file;
    }

    boolean written() {
        return written;
    }

    void write(String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            written = true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
