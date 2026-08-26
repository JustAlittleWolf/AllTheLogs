package me.wolfii.allthelogs.data.importer;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.discover.ImportObserver;
import me.wolfii.allthelogs.data.discover.LogCandidate;
import me.wolfii.allthelogs.data.discover.LogDiscovery;
import me.wolfii.allthelogs.data.store.LogWriter;
import me.wolfii.allthelogs.data.store.MessageDictionary;
import me.wolfii.allthelogs.data.store.PreparedLog;
import me.wolfii.allthelogs.data.store.Schema;
import me.wolfii.allthelogs.data.store.SourceKind;
import org.duckdb.DuckDBConnection;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Discovers log files, parses them in parallel, and writes them on the calling thread.
 * <p>
 * The writer stays on the calling thread because DuckDB's appender is confined to the thread that
 * created it. Discovery runs in the background so reading archives overlaps with writing.
 */
public final class LogImporter {
    private static final int WRITE_QUEUE_CAPACITY = 64;
    private static final PreparedLog END_OF_STREAM = new PreparedLog(
        "", SourceKind.FILE, "", "", LocalDate.EPOCH, "", List.of(), List.of(),
        false, null, null);

    private final DuckDBConnection connection;
    private final MessageDictionary messages;

    public LogImporter(DuckDBConnection connection, MessageDictionary messages) {
        this.connection = connection;
        this.messages = messages;
    }

    /**
     * Imports every matching log file under {@code directory}.
     */
    public ImportResult importDirectory(Path directory, ImportOptions options, Consumer<ImportProgress> progress) {
        return runImport(options, discovery -> discovery.discoverDirectory(directory), progress);
    }

    /**
     * Imports every matching log file inside {@code archive}.
     */
    public ImportResult importArchive(Path archive, ImportOptions options, Consumer<ImportProgress> progress) {
        return runImport(options, discovery -> discovery.discoverArchive(archive), progress);
    }

    private ImportResult runImport(ImportOptions options, Consumer<LogDiscovery> walk, Consumer<ImportProgress> progress) {
        ImportResult result = importIntoStore(options, walk, progress);
        if (result.importedFiles() > 0) {
            try (Statement statement = connection.createStatement()) {
                Schema.clusterEntries(statement);
            } catch (SQLException e) {
                throw new LogDataException("could not cluster imported chat entries", e);
            }
        }
        return result;
    }

    private ImportResult importIntoStore(ImportOptions options, Consumer<LogDiscovery> walk, Consumer<ImportProgress> progress) {
        BlockingQueue<PreparedLog> queue = new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY);
        List<ImportResult.Failure> parseFailures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        ImportObserver observer = new ImportObserver(progress);

        try (LogWriter writer = new LogWriter(connection, messages)) {
            var failureRef = new AtomicReference<RuntimeException>();
            ExecutorService parsers = Executors.newFixedThreadPool(options.parallelism());
            LogDiscovery discovery = new LogDiscovery(options, candidate -> {
                if (options.skipAlreadyImported() && writer.isAlreadyImported(candidate.sourcePath(), candidate.entryPath())) {
                    skipped.incrementAndGet();
                    observer.fileCompleted();
                    return;
                }
                parsers.execute(() -> {
                    try {
                        PreparedLog prepared = LogPreparer.prepare(candidate, options.timezone());
                        // Not stored: missing timestamps, or no chat and no resource-manager reload.
                        // Empty files are stored logs that have no chat lines.
                        if (prepared.firstLineTime() == null || prepared.lastLineTime() == null
                            || (prepared.messages().isEmpty() && !prepared.resourceManagerReloaded())) {
                            skipped.incrementAndGet();
                            observer.fileCompleted();
                            return;
                        }
                        if (prepared.messages().isEmpty()) {
                            empty.incrementAndGet();
                        }
                        queue.put(prepared);
                    } catch (IOException e) {
                        parseFailures.add(new ImportResult.Failure(failurePath(candidate),
                            "could not parse: " + e.getMessage()));
                        observer.fileCompleted();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        observer.fileCompleted();
                    }
                });
            }, observer);

            Thread discoverer = new Thread(() -> {
                try {
                    walk.accept(discovery);
                } catch (RuntimeException e) {
                    failureRef.set(e);
                } finally {
                    observer.discoveryFinished();
                    parsers.shutdown();
                    try {
                        awaitTermination(parsers);
                        queue.put(END_OF_STREAM);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "allthelogs-discovery");
            discoverer.start();

            try {
                while (true) {
                    PreparedLog log = queue.take();
                    if (log == END_OF_STREAM) break;
                    writer.write(log);
                    observer.fileCompleted(sourceOf(log));
                }
            } finally {
                drain(queue, discoverer);
            }

            observer.finished();

            RuntimeException discoveryFailure = failureRef.get();
            if (discoveryFailure != null) throw discoveryFailure;

            writer.deduplicate();

            List<ImportResult.Failure> failures = new ArrayList<>(discovery.failures());
            failures.addAll(parseFailures);
            return new ImportResult(writer.writtenFiles(), skipped.get(), empty.get(), writer.writtenEntries(),
                List.copyOf(failures));
        } catch (SQLException e) {
            throw new LogDataException("could not write imported logs", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LogDataException("import was interrupted", e);
        }
    }

    private static LogSource sourceOf(PreparedLog log) {
        return switch (log.sourceKind()) {
            case FILE -> new LogSource.File(Path.of(log.sourcePath()));
            case ARCHIVE -> new LogSource.Archive(Path.of(log.sourcePath()), log.entryPath());
            case SESSION -> new LogSource.Session();
        };
    }

    private static String failurePath(LogCandidate candidate) {
        if (candidate.sourceKind() == SourceKind.FILE || candidate.entryPath().isEmpty()) {
            return candidate.sourcePath();
        }
        return candidate.sourcePath() + LogDiscovery.ARCHIVE_SEPARATOR + candidate.entryPath();
    }

    /** Drains leftover work so parsing threads do not stay blocked on a full queue if writing failed. */
    private static void drain(BlockingQueue<PreparedLog> queue, Thread discoverer) throws InterruptedException {
        while (discoverer.isAlive() || !queue.isEmpty()) {
            queue.poll(50, TimeUnit.MILLISECONDS);
        }
        discoverer.join();
    }

    private static void awaitTermination(ExecutorService parsers) throws InterruptedException {
        while (!parsers.awaitTermination(1, TimeUnit.MINUTES)) {
        }
    }
}
