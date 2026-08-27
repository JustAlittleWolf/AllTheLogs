package me.wolfii.allthelogs.data.importer;

import me.wolfii.allthelogs.data.*;
import me.wolfii.allthelogs.data.importer.discover.FileCountEstimator;
import me.wolfii.allthelogs.data.importer.discover.ImportObserver;
import me.wolfii.allthelogs.data.importer.discover.LogCandidate;
import me.wolfii.allthelogs.data.importer.discover.LogDiscovery;
import me.wolfii.allthelogs.data.store.LogWriter;
import me.wolfii.allthelogs.data.store.PreparedLog;
import me.wolfii.allthelogs.data.store.SourceKind;
import me.wolfii.allthelogs.data.store.StoredSources;
import org.duckdb.DuckDBConnection;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Discovers log files, parses them in parallel, and writes them on the calling thread.
 * <p>
 * The writer stays on the calling thread because DuckDB's appender is confined to the thread that
 * created it. Discovery runs in the background so reading archives overlaps with writing.
 */
public final class LogImporter {
    private static final int WRITE_QUEUE_CAPACITY = 64;
    /** Caps how many unread log files sit in memory waiting to parse, so a full re-import cannot balloon. */
    private static final int PARSE_QUEUE_CAPACITY = 16;
    private static final PreparedLog END_OF_STREAM = new PreparedLog(
        "", SourceKind.FILE, "", "", LocalDate.EPOCH, "", List.of(), List.of(), List.of(),
        false, null, null, null, null);

    private final DuckDBConnection connection;

    public LogImporter(DuckDBConnection connection) {
        this.connection = connection;
    }

    private static LogSource sourceOf(PreparedLog log) {
        return StoredSources.fromPrepared(log);
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

    /**
     * Imports every matching log file under {@code directory}.
     */
    public ImportResult importDirectory(Path directory, ImportOptions options, Consumer<ImportProgress> progress,
                                        BooleanSupplier cancelled) {
        int estimate = FileCountEstimator.estimateDirectory(directory, options);
        return importIntoStore(options, discovery -> discovery.discoverDirectory(directory), progress, cancelled,
            estimate);
    }

    /**
     * Imports every matching log file inside {@code archive}.
     */
    public ImportResult importArchive(Path archive, ImportOptions options, Consumer<ImportProgress> progress,
                                      BooleanSupplier cancelled) {
        int estimate = FileCountEstimator.estimateArchive(archive, options);
        return importIntoStore(options, discovery -> discovery.discoverArchive(archive), progress, cancelled, estimate);
    }

    private ImportResult importIntoStore(ImportOptions options, Consumer<LogDiscovery> walk,
                                         Consumer<ImportProgress> progress, BooleanSupplier cancelled,
                                         int estimatedFiles) {
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;
        BlockingQueue<PreparedLog> queue = new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY);
        List<ImportResult.Failure> parseFailures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();
        ImportObserver observer = new ImportObserver(progress);
        observer.setEstimatedFiles(estimatedFiles);

        try (LogWriter writer = new LogWriter(connection)) {
            var failureRef = new AtomicReference<RuntimeException>();
            ExecutorService parsers = new ThreadPoolExecutor(
                options.parallelism(), options.parallelism(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(PARSE_QUEUE_CAPACITY, options.parallelism())),
                new ThreadPoolExecutor.CallerRunsPolicy());
            LogDiscovery discovery = new LogDiscovery(options, candidate -> {
                if (stop.getAsBoolean()) return;
                if (options.skipAlreadyImported() && writer.isAlreadyImported(candidate.sourcePath(), candidate.entryPath())) {
                    skipped.incrementAndGet();
                    observer.fileCompleted();
                    return;
                }
                try {
                    parsers.execute(() -> {
                        if (stop.getAsBoolean()) {
                            observer.fileCompleted();
                            return;
                        }
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
                            if (prepared.sessionId() != null && writer.hasSession(prepared.sessionId())) {
                                skipped.incrementAndGet();
                                observer.fileCompleted();
                                return;
                            }
                            if (prepared.messages().isEmpty()) {
                                empty.incrementAndGet();
                            }
                            queue.put(prepared);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            observer.fileCompleted();
                        } catch (Exception e) {
                            parseFailures.add(new ImportResult.Failure(failurePath(candidate),
                                "could not parse: " + e.getMessage()));
                            observer.fileCompleted();
                        }
                    });
                } catch (RejectedExecutionException e) {
                    observer.fileCompleted();
                }
            }, observer, stop);

            Thread discoverer = new Thread(() -> {
                try {
                    if (!stop.getAsBoolean()) {
                        walk.accept(discovery);
                    }
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
                    if (stop.getAsBoolean()) {
                        parsers.shutdownNow();
                        discoverer.interrupt();
                        break;
                    }
                    PreparedLog log = queue.poll(50, TimeUnit.MILLISECONDS);
                    if (log == null) continue;
                    if (log == END_OF_STREAM) break;
                    writer.write(log);
                    observer.fileCompleted(sourceOf(log));
                }
            } finally {
                drain(queue, discoverer);
            }

            observer.finished();

            RuntimeException discoveryFailure = failureRef.get();
            if (discoveryFailure != null && !stop.getAsBoolean()) throw discoveryFailure;

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
}
