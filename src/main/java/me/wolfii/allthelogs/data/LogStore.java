package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.importer.LogImporter;
import me.wolfii.allthelogs.data.query.ChatQueries;
import me.wolfii.allthelogs.data.store.Schema;
import me.wolfii.allthelogs.data.store.SessionCapture;
import me.wolfii.allthelogs.data.store.StoreConnections;
import me.wolfii.allthelogs.data.store.StoreOptimizer;
import org.duckdb.DuckDBConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Imports Minecraft chat logs into a single portable database file and queries them back as plain Java objects.
 * <p>
 * The store is backed by an embedded DuckDB database, which needs no server, works on every major operating system
 * and keeps everything in the one file passed to {@link #open(Path)}. Callers never see the schema: imports take
 * file system paths, queries take a {@link ChatQuery} and return {@link ChatEntry} records with their {@link ChatLog}
 * already resolved.
 * {@snippet :
 * try (LogStore store = LogStore.open(Path.of("logs.duckdb"))) {
 *     store.importDirectory(Path.of("C:/Users/me/AppData/Roaming/.minecraft"),
 *             ImportOptions.defaults().withPathMatcher("**" + "/logs/**"),
 *             progress -> System.out.println(progress.completedFiles() + "/" + progress.discoveredFiles()
 *                     + " " + progress.current()));
 *     List<ChatEntry> hits = store.findEntries(ChatQuery.all().withSubstring("welcome").withContextLines(2));
 * }
 *}
 * <p>
 * A store is not safe for use from several threads at once; imports parallelise internally. Session capture
 * ({@link #startSession(String)}, {@link #importSessionMessage(String)}) does not report progress.
 */
public final class LogStore implements AutoCloseable {
    private DuckDBConnection connection;
    private final Path databasePath;
    private LogImporter importer;
    private final SessionCapture sessions;
    private ChatQueries queries;

    private LogStore(DuckDBConnection connection, Path databasePath) {
        this.databasePath = databasePath;
        this.sessions = new SessionCapture(connection);
        bind(connection);
    }

    private void bind(DuckDBConnection connection) {
        this.connection = connection;
        this.importer = new LogImporter(connection);
        this.sessions.attach(connection);
        this.queries = new ChatQueries(connection);
    }

    /**
     * Opens, and if needed creates, the database at {@code databasePath}.
     *
     * @throws LogDataException if the file cannot be opened or its schema cannot be created
     */
    public static LogStore open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolute = databasePath.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return new LogStore(StoreConnections.openFile(absolute), absolute);
        } catch (IOException | SQLException e) {
            throw new LogDataException("could not open log database at " + absolute, e);
        }
    }

    /**
     * Opens a store that lives only in memory, for tests and throwaway analysis.
     */
    static LogStore openInMemory() {
        try {
            return new LogStore(StoreConnections.openInMemory(), null);
        } catch (SQLException e) {
            throw new LogDataException("could not open in-memory log database", e);
        }
    }

    private static long onDiskSize(Path database) {
        return sizeIfPresent(database) + sizeIfPresent(walPath(database));
    }

    private static Path walPath(Path database) {
        return database.resolveSibling(database.getFileName().toString() + ".wal");
    }

    private static long sizeIfPresent(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            throw new LogDataException("could not read size of " + path, e);
        }
    }

    /**
     * The file this store is backed by, or empty for an in-memory store.
     */
    public Optional<Path> databasePath() {
        return Optional.ofNullable(databasePath);
    }

    /**
     * Imports every log file found below {@code directory} with {@link ImportOptions#defaults()}.
     */
    public ImportResult importDirectory(Path directory) {
        return importDirectory(directory, ImportOptions.defaults(), null);
    }

    /**
     * Imports every log file found below {@code directory} with {@link ImportOptions#defaults()}, reporting progress
     * to {@code progress}.
     */
    public ImportResult importDirectory(Path directory, Consumer<ImportProgress> progress) {
        return importDirectory(directory, ImportOptions.defaults(), progress);
    }

    /**
     * Imports every log file found below {@code directory}.
     *
     * @param directory the directory to walk; {@link ImportOptions#pathMatcher()} is relative to this directory. Each
     *                  imported log is recorded as a {@link LogSource.File} pointing at the file itself
     * @throws LogDataException if {@code directory} is not a directory, or the database rejects the writes
     */
    public ImportResult importDirectory(Path directory, ImportOptions options) {
        return importDirectory(directory, options, null);
    }

    /**
     * Imports every log file found below {@code directory}.
     *
     * @param directory the directory to walk; {@link ImportOptions#pathMatcher()} is relative to this directory. Each
     *                  imported log is recorded as a {@link LogSource.File} pointing at the file itself
     * @param progress  called as files are found and processed, with the current log file or archive; {@code null} to
     *                  ignore progress. Session capture does not report progress
     * @throws LogDataException if {@code directory} is not a directory, or the database rejects the writes
     */
    public ImportResult importDirectory(Path directory, ImportOptions options, Consumer<ImportProgress> progress) {
        return importDirectory(directory, options, progress, () -> false);
    }

    /**
     * Imports every log file found below {@code directory}. {@code cancelled} is polled during discovery and
     * writing; a {@code true} result stops the run and returns whatever was stored so far.
     */
    public ImportResult importDirectory(Path directory, ImportOptions options, Consumer<ImportProgress> progress,
                                        BooleanSupplier cancelled) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(options, "options");
        return importThenOptimize(
            sink -> importer.importDirectory(directory, options, sink, cancelled), progress);
    }

    /**
     * Imports every log file inside {@code archive} with {@link ImportOptions#defaults()}.
     */
    public ImportResult importArchive(Path archive) {
        return importArchive(archive, ImportOptions.defaults(), null);
    }

    /**
     * Imports every log file inside {@code archive} with {@link ImportOptions#defaults()}, reporting progress to
     * {@code progress}.
     */
    public ImportResult importArchive(Path archive, Consumer<ImportProgress> progress) {
        return importArchive(archive, ImportOptions.defaults(), progress);
    }

    /**
     * Imports every log file inside {@code archive}. Zip, 7z, tar and tar.gz archives are supported.
     *
     * @param archive the archive to read; each result is a {@link LogSource.Archive} with that file and the path of the
     *                log inside it
     * @throws LogDataException if {@code archive} is not a file, or the database rejects the writes
     */
    public ImportResult importArchive(Path archive, ImportOptions options) {
        return importArchive(archive, options, null);
    }

    /**
     * Imports every log file inside {@code archive}. Zip, 7z, tar and tar.gz archives are supported.
     *
     * @param archive  the archive to read; each result is a {@link LogSource.Archive} with that file and the path of the
     *                 log inside it
     * @param progress called as files are found and processed, with the current log file or archive; {@code null} to
     *                 ignore progress. Session capture does not report progress
     * @throws LogDataException if {@code archive} is not a file, or the database rejects the writes
     */
    public ImportResult importArchive(Path archive, ImportOptions options, Consumer<ImportProgress> progress) {
        return importArchive(archive, options, progress, () -> false);
    }

    /**
     * Imports every log file inside {@code archive}. {@code cancelled} is polled during discovery and writing;
     * a {@code true} result stops the run and returns whatever was stored so far.
     */
    public ImportResult importArchive(Path archive, ImportOptions options, Consumer<ImportProgress> progress,
                                      BooleanSupplier cancelled) {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(options, "options");
        return importThenOptimize(
            sink -> importer.importArchive(archive, options, sink, cancelled), progress);
    }

    private ImportResult importThenOptimize(Function<Consumer<ImportProgress>, ImportResult> importCall,
                                            Consumer<ImportProgress> progress) {
        ImportProgressTracker tracker = new ImportProgressTracker(progress);
        ImportResult result = importCall.apply(tracker);
        optimizeAfterImport(result, tracker);
        return result;
    }

    /**
     * After a batch import, rewrite {@code chat_entry} oldest-first (chunking) and compact the on-disk
     * file. Live session inserts already arrive in time order, so this stays off the hot path.
     */
    private void optimizeAfterImport(ImportResult result, ImportProgressTracker tracker) {
        if (result.importedFiles() <= 0) {
            tracker.complete();
            return;
        }
        try {
            tracker.phase(ImportPhase.CHUNKING, 0d);
            try (Statement statement = connection.createStatement()) {
                Schema.clusterEntries(statement, fraction -> tracker.phase(ImportPhase.CHUNKING, fraction));
            }
            tracker.phase(ImportPhase.OPTIMIZING, 0d);
            try (Statement statement = connection.createStatement()) {
                StoreOptimizer.analyzeAndCheckpoint(statement);
            }
            tracker.phase(ImportPhase.OPTIMIZING, 0.3);
            if (databasePath != null) {
                bind(StoreOptimizer.replaceWithCompactCopy(connection, databasePath));
            }
            tracker.complete();
        } catch (SQLException | IOException e) {
            throw new LogDataException("could not optimize imported chat entries", e);
        }
    }

    /**
     * Starts a capture session for a running Minecraft client and creates a {@link ChatLog} for it.
     * <p>
     * Chat lines imported with {@link #importSessionMessage(String)} are stored against this log. Its
     * {@link ChatLog#startTime()} is the session start; {@link #importSessionMessage(String, LocalDateTime)} and
     * {@link #updateSessionEndTime(LocalDateTime)} update {@link ChatLog#endTime()} as the session continues.
     * Starting another session leaves the previous log in place and switches subsequent imports to the new one.
     * <p>
     * The log's {@link ChatLog#source()} is a {@link LogSource.Session} with a unique id. Callers that capture from
     * a running game should write {@link me.wolfii.allthelogs.data.store.SessionMarker#message(String)} to the Minecraft log (not as chat) so a later
     * import of that file is skipped when this session is already stored.
     *
     * @param minecraftVersion the version of the running game
     * @return the created chat log, with no entries yet
     * @throws LogDataException if the session cannot be written
     */
    public ChatLog startSession(String minecraftVersion) {
        return startSession(minecraftVersion, LocalDateTime.now());
    }

    /**
     * Starts a capture session at an explicit time.
     *
     * @see #startSession(String)
     */
    public ChatLog startSession(String minecraftVersion, LocalDateTime startedAt) {
        return sessions.start(minecraftVersion, startedAt);
    }

    /**
     * Imports a single chat line from the running client into the current session, stamped with the current time.
     * <p>
     * A line that repeats the timestamp and text of an entry already stored is dropped. Live capture of a play
     * session is not duplicated on a later file import when that log contains this session's
     * {@link me.wolfii.allthelogs.data.store.SessionMarker}.
     *
     * @param message the chat line as the game rendered it; formatting codes are stripped like on import
     * @return {@code true} if the entry was stored, {@code false} if it was dropped as a duplicate
     * @throws LogDataException if no session is active, or the entry cannot be written
     */
    public boolean importSessionMessage(String message) {
        return importSessionMessage(message, LocalDateTime.now());
    }

    /**
     * Imports a single chat line from the running client into the current session at an explicit time.
     *
     * @see #importSessionMessage(String)
     */
    public boolean importSessionMessage(String message, LocalDateTime timestamp) {
        return sessions.importMessage(message, timestamp);
    }

    /**
     * Imports an already-stripped live chat line with flattened packed formatting.
     *
     * @see #importSessionMessage(String)
     */
    public boolean importSessionMessage(String message, long[] formatting) {
        return importSessionMessage(message, formatting, LocalDateTime.now());
    }

    /**
     * Imports an already-stripped live chat line with flattened packed formatting at an explicit time.
     *
     * @see #importSessionMessage(String)
     */
    public boolean importSessionMessage(String message, long[] formatting, LocalDateTime timestamp) {
        return sessions.importMessage(message, formatting, timestamp);
    }

    /**
     * Updates {@link ChatLog#endTime()} of the current session, without storing a chat line.
     * <p>
     * Whole seconds only, matching {@link #importSessionMessage(String, LocalDateTime)}. If {@code timestamp} is
     * earlier than the time already stored, the existing end time is kept.
     *
     * @throws LogDataException if no session is active, or the update cannot be written
     */
    public void updateSessionEndTime(LocalDateTime timestamp) {
        sessions.updateEndTime(timestamp);
    }

    /**
     * Returns every entry matching {@code query}, resolved into records with their chat log attached.
     * <p>
     * Only chat logs that appear in the result are loaded; the listing API {@link #chatLogs()} still reads every file.
     *
     * @throws LogDataException if the query is rejected, e.g. because its regex is malformed
     */
    public List<ChatEntry> findEntries(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        return queries.findEntries(query);
    }

    /**
     * Unpaged match count, first/last timestamps, and per-day counts for {@code query}. Ignores paging
     * and context. One date aggregation, not a load of every matching row.
     */
    public MatchSummary summarizeMatches(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        return queries.summarizeMatches(query);
    }

    /**
     * Number of matching entries for {@code query}. Honours offset and limit; ignores context lines.
     * Callers that want every match for a filter should drop the page offset and pass a negative limit.
     */
    public long countMatches(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        return queries.countMatches(query);
    }

    /**
     * Chat lines from {@code log} within {@code radius} of {@code lineIndex}, inclusive of the centre line.
     */
    public List<ChatEntry> entriesAround(ChatLog log, int lineIndex, int radius) {
        return entriesAround(log, lineIndex, radius, radius);
    }

    /**
     * Chat lines from {@code log} between {@code lineIndex - before} and {@code lineIndex + after}.
     */
    public List<ChatEntry> entriesAround(ChatLog log, int lineIndex, int before, int after) {
        Objects.requireNonNull(log, "log");
        return queries.entriesAround(log, lineIndex, before, after);
    }

    /**
     * Returns every stored entry, oldest first. Convenience for {@code findEntries(ChatQuery.all())}.
     */
    public List<ChatEntry> allEntries() {
        return findEntries(ChatQuery.all());
    }

    /**
     * Returns every imported chat log, ordered by date.
     */
    public List<ChatLog> chatLogs() {
        return queries.chatLogs();
    }

    /**
     * Summarises the stored logs: distinct Minecraft versions, the earliest and latest log dates, how many logs and
     * chat entries are stored, and the database size in bytes.
     */
    public LogStoreMetadata metadata() {
        return queries.metadata(databaseSizeBytes());
    }

    private long databaseSizeBytes() {
        if (databasePath == null) {
            return queries.reportedDatabaseSize();
        }
        return onDiskSize(databasePath);
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new LogDataException("could not close the log database", e);
        }
    }

    /**
     * Remembers the last file-count snapshot so chunking and optimization can keep reporting overall progress.
     */
    private static final class ImportProgressTracker implements Consumer<ImportProgress> {
        private final Consumer<ImportProgress> downstream;
        private ImportProgress last = new ImportProgress(0, 0, 0, true, null);

        private ImportProgressTracker(Consumer<ImportProgress> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void accept(ImportProgress snapshot) {
            last = snapshot;
            if (downstream != null) downstream.accept(snapshot);
        }

        private void phase(ImportPhase phase, double phaseFraction) {
            accept(last.withPhase(phase, phaseFraction));
        }

        private void complete() {
            phase(ImportPhase.OPTIMIZING, 1d);
        }
    }
}
