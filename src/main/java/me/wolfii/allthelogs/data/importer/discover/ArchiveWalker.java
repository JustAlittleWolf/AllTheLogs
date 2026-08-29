package me.wolfii.allthelogs.data.importer.discover;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.store.SourceKind;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Reads zip, 7z, tar, and tar.gz archives and emits log files, including nested archives when enabled.
 */
final class ArchiveWalker {
    private static final int STREAM_BUFFER = 1 << 16;

    private final ImportOptions options;
    private final Pattern pathMatcher;
    private final ImportObserver observer;
    private final BooleanSupplier cancelled;
    private final List<ImportResult.Failure> failures;
    private final BiPredicate<String, String> skipUnopened;
    private final Runnable onSkipped;
    private final LogOffer offerLog;

    ArchiveWalker(ImportOptions options, Pattern pathMatcher, ImportObserver observer,
                  BooleanSupplier cancelled, List<ImportResult.Failure> failures,
                  BiPredicate<String, String> skipUnopened, Runnable onSkipped, LogOffer offerLog) {
        this.options = options;
        this.pathMatcher = pathMatcher;
        this.observer = observer;
        this.cancelled = cancelled;
        this.failures = failures;
        this.skipUnopened = skipUnopened;
        this.onSkipped = onSkipped;
        this.offerLog = offerLog;
    }

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

    /**
     * @param sourcePath  absolute path of the archive file recorded on the resulting candidates
     * @param description human readable location of this archive, used in failure messages
     * @param prefix      path prefix that entries of this archive get inside the outermost archive
     * @param globPrefix  path prefix used for {@link ImportOptions#pathMatcher()}, relative to the import root
     */
    void read(Path archive, String sourcePath, String description, String prefix, String globPrefix,
              String archiveName) {
        String archiveEntry = prefix.isEmpty() ? "" : prefix.substring(0, prefix.length() - LogDiscovery.ARCHIVE_SEPARATOR.length());
        if (cancelled.getAsBoolean()) return;
        observer.workingOnArchive(Path.of(sourcePath), archiveEntry);
        try {
            if (archiveName.toLowerCase(Locale.ROOT).endsWith(".7z")) {
                readSevenZip(archive, sourcePath, description, prefix, globPrefix);
            } else {
                readStreamedArchive(archive, sourcePath, description, prefix, globPrefix);
            }
        } catch (IOException | RuntimeException e) {
            failures.add(new ImportResult.Failure(description, "could not read archive: " + e));
        }
    }

    private void readSevenZip(Path archive, String sourcePath, String description, String prefix, String globPrefix)
        throws IOException {
        try (SevenZFile file = SevenZFile.builder().setPath(archive).get()) {
            SevenZArchiveEntry entry;
            while ((entry = file.getNextEntry()) != null) {
                if (cancelled.getAsBoolean()) return;
                if (entry.isDirectory()) continue;
                Instant modified = entry.getHasLastModifiedDate()
                    ? entry.getLastModifiedDate().toInstant() : null;
                handleArchiveEntry(entry.getName(), modified, sourcePath, description, prefix, globPrefix,
                    entry.getSize(), () -> new SevenZEntryStream(file));
            }
        }
    }

    private void readStreamedArchive(Path archive, String sourcePath, String description, String prefix,
                                     String globPrefix) throws IOException {
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
                    if (cancelled.getAsBoolean()) return;
                    if (entry.isDirectory() || !stream.canReadEntryData(entry)) continue;
                    Instant modified = entry.getLastModifiedDate() == null ? null
                        : toInstant(entry.getLastModifiedDate().getTime());
                    handleArchiveEntry(entry.getName(), modified, sourcePath, description, prefix, globPrefix,
                        entry.getSize(), () -> new NonClosingStream(stream));
                }
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
        } finally {
            input.close();
        }
    }

    private void handleArchiveEntry(String rawName, Instant modified, String sourcePath, String description,
                                    String prefix, String globPrefix, long size, ContentSupplier content)
        throws IOException {
        if (cancelled.getAsBoolean()) return;
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("./")) normalized = normalized.substring(2);
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        String entryPath = prefix + normalized;
        boolean nested = normalized.indexOf('/') >= 0;
        if (nested && !options.recursive()) return;

        if (LogDiscovery.isLogFile(name)) {
            if (!matches(globPrefix + normalized)) return;
            observer.fileStarted(new LogSource.Archive(Path.of(sourcePath), entryPath));
            // Path skip before reading bytes; hash skip happens in offerLog after the read.
            if (skipUnopened.test(sourcePath, entryPath)) {
                onSkipped.run();
                observer.fileCompleted();
                return;
            }
            byte[] bytes;
            try (InputStream stream = content.open()) {
                bytes = readFully(stream, size);
            } catch (IOException e) {
                observer.fileCompleted();
                throw e;
            }
            offerLog.offer(name, SourceKind.ARCHIVE, sourcePath, entryPath, modified, bytes);
        } else if (options.nestedArchives() && LogDiscovery.isArchive(name)) {
            Path temporary = Files.createTempFile("allthelogs-", "-" + name);
            try {
                try (InputStream stream = content.open()) {
                    Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                read(temporary, sourcePath, description + LogDiscovery.ARCHIVE_SEPARATOR + normalized,
                    entryPath + LogDiscovery.ARCHIVE_SEPARATOR, globPrefix + normalized + LogDiscovery.ARCHIVE_SEPARATOR, name);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private boolean matches(String entryPath) {
        return pathMatcher == null || pathMatcher.matcher(entryPath).matches();
    }

    @FunctionalInterface
    interface LogOffer {
        boolean offer(String fileName, SourceKind kind, String sourcePath, String entryPath, Instant modified,
                      byte[] bytes);
    }

    private interface ContentSupplier {
        InputStream open() throws IOException;
    }

    /**
     * Hides {@link InputStream#close()} from consumers so that reading one entry cannot end the whole archive stream.
     */
    private static final class NonClosingStream extends FilterInputStream {
        private NonClosingStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() {
        }
    }

    /**
     * Exposes the current entry of a {@link SevenZFile} as a stream, since the sequential API reads through the file
     * itself.
     */
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
