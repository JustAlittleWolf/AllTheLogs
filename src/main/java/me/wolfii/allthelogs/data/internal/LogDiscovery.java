package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogDataException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/// Walks directories and archives and hands every log file that passes the filters to a consumer.
///
/// Discovery is single threaded on purpose: it is dominated by sequential IO, and archive formats such as 7z cannot be
/// read concurrently anyway. The expensive work, parsing and inserting, happens on the consumer side.
public final class LogDiscovery {
    /// Separates the archive path from the path inside it, matching the convention of JAR URLs.
    public static final String ARCHIVE_SEPARATOR = "!/";
    private static final List<String> LOG_SUFFIXES = List.of(".log", ".log.gz");
    private static final List<String> ARCHIVE_SUFFIXES = List.of(".zip", ".7z", ".tar", ".tar.gz", ".tgz");
    private static final int STREAM_BUFFER = 1 << 16;

    private final ImportOptions options;
    private final Pattern pathMatcher;
    private final Consumer<LogCandidate> consumer;
    private final List<ImportResult.Failure> failures = new ArrayList<>();

    public LogDiscovery(ImportOptions options, Consumer<LogCandidate> consumer) {
        this.options = options;
        this.pathMatcher = options.pathMatcher() == null ? null : Globs.compile(options.pathMatcher());
        this.consumer = consumer;
    }

    /// Reads an entry whose length the archive already told us, so the buffer is allocated exactly once.
    private static byte[] readFully(InputStream stream, long expectedSize) throws IOException {
        if (expectedSize > Integer.MAX_VALUE) {
            throw new IOException("archive entry is too large to read: " + expectedSize + " bytes");
        }
        if (expectedSize < 0) return stream.readAllBytes();
        byte[] bytes = new byte[(int) expectedSize];
        int read = stream.readNBytes(bytes, 0, bytes.length);
        if (read < bytes.length) {
            return Arrays.copyOf(bytes, read);
        }
        byte[] remainder = stream.readAllBytes();
        if (remainder.length == 0) return bytes;
        ByteArrayOutputStream combined = new ByteArrayOutputStream(bytes.length + remainder.length);
        combined.write(bytes);
        combined.write(remainder);
        return combined.toByteArray();
    }

    private static Instant toInstant(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis);
    }

    private static boolean isLogFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return LOG_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return ARCHIVE_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    public List<ImportResult.Failure> failures() {
        return List.copyOf(failures);
    }

    /// Walks a directory tree, emitting every log file and, if enabled, descending into archives found on the way.
    public void discoverDirectory(Path root) {
        Path absolute = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new LogDataException("not a directory: " + absolute);
        }
        walk(absolute, absolute, "");
    }

    /// Reads an archive, emitting every log file inside it.
    public void discoverArchive(Path archive) {
        Path absolute = archive.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new LogDataException("not a file: " + absolute);
        }
        readArchive(absolute, absolute.toString(), absolute.toString(), "", absolute.getFileName().toString());
    }

    private void walk(Path root, Path directory, String prefix) {
        List<Path> children;
        try (var stream = Files.list(directory)) {
            children = stream.sorted(Comparator.comparing(Path::getFileName)).toList();
        } catch (IOException e) {
            failures.add(new ImportResult.Failure(directory.toString(), "could not list directory: " + e.getMessage()));
            return;
        }
        for (Path child : children) {
            String name = child.getFileName().toString();
            String entryPath = prefix.isEmpty() ? name : prefix + "/" + name;
            if (Files.isDirectory(child)) {
                if (options.recursive()) walk(root, child, entryPath);
                continue;
            }
            if (isLogFile(name)) {
                if (!matches(entryPath)) continue;
                byte[] content;
                try {
                    content = Files.readAllBytes(child);
                } catch (IOException e) {
                    failures.add(new ImportResult.Failure(entryPath, "could not read file: " + e.getMessage()));
                    continue;
                }
                consumer.accept(new LogCandidate(name, SourceKind.DIRECTORY, root.toString(), entryPath,
                    lastModified(child), content));
            } else if (options.nestedArchives() && isArchive(name)) {
                readArchive(child, root.toString(), child.toString(), entryPath + ARCHIVE_SEPARATOR, name);
            }
        }
    }

    /// @param sourcePath  the import root recorded on the resulting candidates
    /// @param description human readable location of this archive, used in failure messages
    /// @param prefix      path prefix that entries of this archive get, already ending in [#ARCHIVE_SEPARATOR]
    private void readArchive(Path archive, String sourcePath, String description, String prefix, String archiveName) {
        try {
            if (archiveName.toLowerCase(Locale.ROOT).endsWith(".7z")) {
                readSevenZip(archive, sourcePath, description, prefix);
            } else {
                readStreamedArchive(archive, sourcePath, description, prefix);
            }
        } catch (IOException | RuntimeException e) {
            failures.add(new ImportResult.Failure(description, "could not read archive: " + e));
        }
    }

    private void readSevenZip(Path archive, String sourcePath, String description, String prefix) throws IOException {
        try (SevenZFile file = SevenZFile.builder().setPath(archive).get()) {
            SevenZArchiveEntry entry;
            while ((entry = file.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Instant modified = entry.getHasLastModifiedDate()
                    ? entry.getLastModifiedDate().toInstant() : null;
                handleArchiveEntry(entry.getName(), modified, sourcePath, description, prefix, entry.getSize(),
                    () -> new SevenZEntryStream(file));
            }
        }
    }

    private void readStreamedArchive(Path archive, String sourcePath, String description, String prefix)
        throws IOException {
        InputStream input = new BufferedInputStream(Files.newInputStream(archive), STREAM_BUFFER);
        try {
            try {
                input = new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(input),
                    STREAM_BUFFER);
            } catch (Exception ignored) {
            }
            try (ArchiveInputStream<?> stream = new ArchiveStreamFactory().createArchiveInputStream(input)) {
                ArchiveEntry entry;
                while ((entry = stream.getNextEntry()) != null) {
                    if (entry.isDirectory() || !stream.canReadEntryData(entry)) continue;
                    Instant modified = entry.getLastModifiedDate() == null ? null
                        : toInstant(entry.getLastModifiedDate().getTime());
                    handleArchiveEntry(entry.getName(), modified, sourcePath, description, prefix, entry.getSize(),
                        () -> new NonClosingStream(stream));
                }
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
        } finally {
            input.close();
        }
    }

    private void handleArchiveEntry(String rawName, Instant modified, String sourcePath, String description,
                                    String prefix, long size, ContentSupplier content) throws IOException {
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("./")) normalized = normalized.substring(2);
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        String entryPath = prefix + normalized;
        boolean nested = normalized.indexOf('/') >= 0;
        if (nested && !options.recursive()) return;

        if (isLogFile(name)) {
            if (!matches(entryPath)) return;
            byte[] bytes;
            try (InputStream stream = content.open()) {
                bytes = readFully(stream, size);
            }
            consumer.accept(new LogCandidate(name, SourceKind.ARCHIVE, sourcePath, entryPath, modified, bytes));
        } else if (options.nestedArchives() && isArchive(name)) {
            Path temporary = Files.createTempFile("allthelogs-", "-" + name);
            try {
                try (InputStream stream = content.open()) {
                    Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                readArchive(temporary, sourcePath, description + ARCHIVE_SEPARATOR + normalized,
                    entryPath + ARCHIVE_SEPARATOR, name);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private boolean matches(String entryPath) {
        return pathMatcher == null || pathMatcher.matcher(entryPath).matches();
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    private interface ContentSupplier {
        InputStream open() throws IOException;
    }

    /// Hides [InputStream#close] from consumers so that reading one entry cannot end the whole archive stream.
    private static final class NonClosingStream extends FilterInputStream {
        private NonClosingStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() {
        }
    }

    /// Exposes the current entry of a [SevenZFile] as a stream, since the sequential API reads through the file itself.
    private static final class SevenZEntryStream extends InputStream {
        private final SevenZFile file;

        private SevenZEntryStream(SevenZFile file) {
            this.file = file;
        }

        @Override
        public int read() throws IOException {
            return file.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return file.read(buffer, offset, length);
        }
    }
}
