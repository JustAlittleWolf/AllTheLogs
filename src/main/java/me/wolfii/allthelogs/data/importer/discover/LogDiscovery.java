package me.wolfii.allthelogs.data.importer.discover;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.store.SourceKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Walks directories and archives and hands every log file that passes the filters to a consumer.
 * Discovery is single-threaded: it is dominated by sequential IO, and formats such as 7z cannot be read concurrently.
 * <p>
 * Skip order, cheapest first: known {@code (source_path, entry_path)} is left unopened; otherwise the
 * file is read and hashed, and a known SHA-256 is skipped without parsing.
 */
public final class LogDiscovery {
    /** Separates the archive path from the path inside it, matching the convention of JAR URLs. */
    public static final String ARCHIVE_SEPARATOR = "!/";
    private static final List<String> LOG_SUFFIXES = List.of(".log", ".log.gz");
    private static final List<String> ARCHIVE_SUFFIXES = List.of(".zip", ".7z", ".tar", ".tar.gz", ".tgz");

    private final ImportOptions options;
    private final Pattern pathMatcher;
    private final Consumer<LogCandidate> consumer;
    private final ImportObserver observer;
    private final BooleanSupplier cancelled;
    private final BiPredicate<String, String> skipUnopened;
    private final Runnable onSkipped;
    private final ContentTracker content;
    private final List<ImportResult.Failure> failures = new ArrayList<>();
    private final ArchiveWalker archives;

    public LogDiscovery(ImportOptions options, Consumer<LogCandidate> consumer, ImportObserver observer,
                        BooleanSupplier cancelled, BiPredicate<String, String> skipUnopened, Runnable onSkipped) {
        this(options, consumer, observer, cancelled, skipUnopened, onSkipped, ContentTracker.NONE);
    }

    public LogDiscovery(ImportOptions options, Consumer<LogCandidate> consumer, ImportObserver observer,
                        BooleanSupplier cancelled, BiPredicate<String, String> skipUnopened, Runnable onSkipped,
                        ContentTracker content) {
        this.options = options;
        this.pathMatcher = options.pathMatcher() == null ? null : Globs.compile(options.pathMatcher());
        this.consumer = consumer;
        this.observer = observer;
        this.cancelled = cancelled == null ? () -> false : cancelled;
        this.skipUnopened = skipUnopened == null ? (source, entry) -> false : skipUnopened;
        this.onSkipped = onSkipped == null ? () -> {
        } : onSkipped;
        this.content = content == null ? ContentTracker.NONE : content;
        this.archives = new ArchiveWalker(this.options, this.pathMatcher, this.observer,
            this.cancelled, this.failures, this.skipUnopened, this.onSkipped, this::offerLog);
    }

    boolean offerLog(String fileName, SourceKind kind, String sourcePath, String entryPath, Instant modified,
                     byte[] bytes) {
        String hash = ContentHashes.sha256(bytes);
        if (content.skipHash(hash)) {
            content.remember(sourcePath, entryPath, hash);
            onSkipped.run();
            observer.fileCompleted();
            return false;
        }
        content.noteHash(hash);
        consumer.accept(new LogCandidate(fileName, kind, sourcePath, entryPath, modified, bytes, hash));
        return true;
    }

    static boolean isLogFile(String name) {
        if (name.equalsIgnoreCase("latest.log")) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return LOG_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return ARCHIVE_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private boolean cancelled() {
        return cancelled.getAsBoolean();
    }

    public List<ImportResult.Failure> failures() {
        return List.copyOf(failures);
    }

    /**
     * Walks a directory tree, emitting every log file and, if enabled, descending into archives found on the way.
     */
    public void discoverDirectory(Path root) {
        Path absolute = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new LogDataException("not a directory: " + absolute);
        }
        walk(absolute, absolute, "");
    }

    /**
     * Reads an archive, emitting every log file inside it.
     */
    public void discoverArchive(Path archive) {
        Path absolute = archive.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new LogDataException("not a file: " + absolute);
        }
        archives.read(absolute, absolute.toString(), absolute.toString(), "", "", absolute.getFileName().toString());
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
            if (cancelled()) return;
            String name = child.getFileName().toString();
            String entryPath = prefix.isEmpty() ? name : prefix + "/" + name;
            if (Files.isDirectory(child)) {
                if (options.recursive()) walk(root, child, entryPath);
                continue;
            }
            if (isLogFile(name)) {
                if (!matches(entryPath)) continue;
                Path absoluteFile = child.toAbsolutePath().normalize();
                observer.fileStarted(new LogSource.File(absoluteFile));
                // Path skip before reading bytes; hash skip happens in offerLog after the read.
                if (skipUnopened.test(absoluteFile.toString(), "")) {
                    onSkipped.run();
                    observer.fileCompleted();
                    continue;
                }
                byte[] content;
                try {
                    content = Files.readAllBytes(child);
                } catch (IOException e) {
                    failures.add(new ImportResult.Failure(entryPath, "could not read file: " + e.getMessage()));
                    observer.fileCompleted();
                    continue;
                }
                offerLog(name, SourceKind.FILE, absoluteFile.toString(), "", lastModified(child), content);
            } else if (options.nestedArchives() && isArchive(name)) {
                archives.read(child, child.toAbsolutePath().normalize().toString(), child.toString(),
                    "", entryPath + ARCHIVE_SEPARATOR, name);
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
}
