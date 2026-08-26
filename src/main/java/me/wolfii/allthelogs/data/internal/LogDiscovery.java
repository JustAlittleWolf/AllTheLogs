package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.SourceKind;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/// Walks directories and archives and hands every log file that passes the filters to a consumer.
///
/// Discovery is single threaded on purpose: it is dominated by sequential IO, and archive formats such as 7z cannot be
/// read concurrently anyway. The expensive work, parsing and inserting, happens on the consumer side.
public final class LogDiscovery {
    private static final List<String> LOG_SUFFIXES = List.of(".log", ".log.gz");
    private static final List<String> ARCHIVE_SUFFIXES = List.of(".zip", ".7z", ".tar", ".tar.gz", ".tgz", ".jar");
    /// Separates the archive path from the path inside it, matching the convention of JAR URLs.
    public static final String ARCHIVE_SEPARATOR = "!/";

    private final ImportOptions options;
    private final Pattern pathMatcher;
    private final Consumer<LogCandidate> consumer;
    private final List<ImportResult.Failure> failures = new ArrayList<>();

    public LogDiscovery(ImportOptions options, Consumer<LogCandidate> consumer) {
        this.options = options;
        this.pathMatcher = options.pathMatcher() == null ? null : Globs.compile(options.pathMatcher());
        this.consumer = consumer;
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
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(absolute);
        } catch (IOException e) {
            failures.add(new ImportResult.Failure(absolute.toString(), "could not read archive: " + e.getMessage()));
            return;
        }
        readArchive(bytes, absolute.toString(), absolute.toString(), "", absolute.getFileName().toString());
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
                byte[] content;
                try {
                    content = Files.readAllBytes(child);
                } catch (IOException e) {
                    failures.add(new ImportResult.Failure(entryPath, "could not read archive: " + e.getMessage()));
                    continue;
                }
                readArchive(content, root.toString(), child.toString(), entryPath + ARCHIVE_SEPARATOR, name);
            }
        }
    }

    /// @param sourcePath  the import root recorded on the resulting candidates
    /// @param description human readable location of this archive, used in failure messages
    /// @param prefix      path prefix that entries of this archive get, already ending in [#ARCHIVE_SEPARATOR]
    private void readArchive(byte[] bytes, String sourcePath, String description, String prefix, String archiveName) {
        try {
            if (archiveName.toLowerCase(Locale.ROOT).endsWith(".7z")) {
                readSevenZip(bytes, sourcePath, description, prefix);
            } else {
                readStreamedArchive(bytes, sourcePath, description, prefix);
            }
        } catch (IOException | RuntimeException e) {
            failures.add(new ImportResult.Failure(description, "could not read archive: " + e));
        }
    }

    private void readSevenZip(byte[] bytes, String sourcePath, String description, String prefix) throws IOException {
        try (SevenZFile file = SevenZFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(bytes)).get()) {
            SevenZArchiveEntry next;
            while ((next = file.getNextEntry()) != null) {
                final SevenZArchiveEntry entry = next;
                if (entry.isDirectory()) continue;
                LocalDateTime modified = entry.getHasLastModifiedDate()
                        ? toLocalDateTime(entry.getLastModifiedDate().toInstant().toEpochMilli()) : null;
                handleArchiveEntry(entry.getName(), modified, sourcePath, description, prefix,
                        () -> readFully(file.getInputStream(entry), entry.getSize()));
            }
        }
    }

    private void readStreamedArchive(byte[] bytes, String sourcePath, String description, String prefix)
            throws IOException {
        InputStream input = new BufferedInputStream(new ByteArrayInputStream(bytes));
        try {
            // Unwrap outer compression so that .tar.gz and friends are seen as plain tars.
            input = new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(input));
        } catch (Exception ignored) {
            // Not compressed, or an unknown compressor; either way the archive factory below gets the raw stream.
        }
        try (ArchiveInputStream<?> archive = new ArchiveStreamFactory().createArchiveInputStream(input)) {
            ArchiveEntry next;
            while ((next = archive.getNextEntry()) != null) {
                final ArchiveEntry entry = next;
                if (entry.isDirectory() || !archive.canReadEntryData(entry)) continue;
                LocalDateTime modified = entry.getLastModifiedDate() == null ? null
                        : toLocalDateTime(entry.getLastModifiedDate().getTime());
                handleArchiveEntry(entry.getName(), modified, sourcePath, description, prefix,
                        () -> readFully(archive, entry.getSize()));
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private interface ContentSupplier {
        byte[] get() throws IOException;
    }

    private void handleArchiveEntry(String rawName, LocalDateTime modified, String sourcePath, String description,
                                    String prefix, ContentSupplier content) throws IOException {
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("./")) normalized = normalized.substring(2);
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        String entryPath = prefix + normalized;
        boolean nested = normalized.indexOf('/') >= 0;
        if (nested && !options.recursive()) return;

        if (isLogFile(name)) {
            if (!matches(entryPath)) return;
            consumer.accept(new LogCandidate(name, SourceKind.ARCHIVE, sourcePath, entryPath, modified, content.get()));
        } else if (options.nestedArchives() && isArchive(name)) {
            readArchive(content.get(), sourcePath, description + ARCHIVE_SEPARATOR + normalized,
                    entryPath + ARCHIVE_SEPARATOR, name);
        }
    }

    private boolean matches(String entryPath) {
        return pathMatcher == null || pathMatcher.matcher(entryPath).matches();
    }

    private static byte[] readFully(InputStream stream, long expectedSize) throws IOException {
        if (expectedSize > Integer.MAX_VALUE) {
            throw new IOException("archive entry is too large to read: " + expectedSize + " bytes");
        }
        return stream.readAllBytes();
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private LocalDateTime lastModified(Path path) {
        try {
            return toLocalDateTime(Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isLogFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return LOG_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return ARCHIVE_SUFFIXES.stream().anyMatch(lower::endsWith);
    }
}
