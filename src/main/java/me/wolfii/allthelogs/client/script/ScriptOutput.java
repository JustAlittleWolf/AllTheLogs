package me.wolfii.allthelogs.client.script;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Appends script lines under {@code .allthelogs/scripts/output}. The writer is opened on the first
 * write and kept for the rest of the run.
 */
final class ScriptOutput implements AutoCloseable {
    private final Path file;
    private BufferedWriter writer;

    ScriptOutput(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    Path file() {
        return file;
    }

    boolean written() {
        return writer != null;
    }

    void write(String line) {
        try {
            writer().write(line);
            writer().newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedWriter writer() throws IOException {
        if (writer == null) {
            Files.createDirectories(file.getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        return writer;
    }
}
