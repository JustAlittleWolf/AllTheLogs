package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.internal.*;
import org.duckdb.DuckDBConnection;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/// Imports Minecraft chat logs into a single portable database file and queries them back as plain Java objects.
///
/// The store is backed by an embedded DuckDB database, which needs no server, works on every major operating system
/// and keeps everything in the one file passed to [#open(Path)]. Callers never see the schema: imports take file
/// system paths, queries take a [ChatQuery] and return [ChatEntry] records with their [LogFile] already resolved.
///
/// ```java
/// try (LogStore store = LogStore.open(Path.of("logs.duckdb"))) {
///     store.importDirectory(Path.of("C:/Users/me/AppData/Roaming/.minecraft"),
///             ImportOptions.defaults().withPathMatcher("**&#47;logs&#47;**"));
///     List<ChatEntry> hits = store.query(ChatQuery.all().withSubstring("welcome").withContextLines(2));
/// }
/// ```
///
/// A store is not safe for use from several threads at once; imports parallelise internally.
public final class LogStore implements AutoCloseable {
    /// Legacy Windows code page that old Minecraft launchers wrote logs in, before UTF-8 became the norm.
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    /// How many parsed logs may wait for the writer before parsing threads block. Keeps memory bounded while still
    /// letting the writer stay busy.
    private static final int WRITE_QUEUE_CAPACITY = 64;
    /// Recorded as the source path of the synthetic log files that hold live captured entries.
    private static final String LIVE_SOURCE_PATH = "<live>";
    /// Sentinel that tells the writer loop that no more logs are coming.
    private static final PreparedLog END_OF_STREAM = new PreparedLog(
        "", SourceKind.DIRECTORY, "", "", LocalDate.EPOCH, DateSource.FILE_NAME, "", null, List.of(), List.of(),
        false, null, null);

    private final DuckDBConnection connection;
    private final Path databasePath;

    private LogStore(DuckDBConnection connection, Path databasePath) {
        this.connection = connection;
        this.databasePath = databasePath;
    }

    /// Connection settings that decide how compactly the file is written.
    ///
    /// DuckDB defaults new files to the storage format of v0.10.2 so that old readers can still open them, and that
    /// format predates the string compression this data benefits from most. Asking for the newest format unlocks
    /// `DICT_FSST`, which deduplicates the many repeated chat lines and then compresses the remaining dictionary,
    /// roughly halving the space messages take. Chat lines are far shorter than the 4096 byte threshold at which
    private static Properties storageSettings() {
        Properties settings = new Properties();
        settings.setProperty("storage_compatibility_version", "latest");
        return settings;
    }

    /// Opens, and if needed creates, the database at `databasePath`.
    ///
    /// @throws LogDataException if the file cannot be opened or its schema cannot be created
    public static LogStore open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Path absolute = databasePath.toAbsolutePath().normalize();
        try {
            var connection = (DuckDBConnection) DriverManager.getConnection(
                "jdbc:duckdb:" + absolute, storageSettings());
            try (Statement statement = connection.createStatement()) {
                Schema.create(statement);
            } catch (SQLException e) {
                connection.close();
                throw e;
            }
            return new LogStore(connection, absolute);
        } catch (SQLException e) {
            throw new LogDataException("could not open log database at " + absolute, e);
        }
    }

    /// Opens a store that lives only in memory, for tests and throwaway analysis.
    public static LogStore openInMemory() {
        try {
            var connection = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:", storageSettings());
            try (Statement statement = connection.createStatement()) {
                Schema.create(statement);
            }
            return new LogStore(connection, null);
        } catch (SQLException e) {
            throw new LogDataException("could not open in-memory log database", e);
        }
    }

    private static void drain(BlockingQueue<PreparedLog> queue, Thread discoverer) throws InterruptedException {
        while (discoverer.isAlive() || !queue.isEmpty()) {
            queue.poll(50, TimeUnit.MILLISECONDS);
        }
        discoverer.join();
    }

    private static void awaitTermination(ExecutorService parsers) throws InterruptedException {
        while (!parsers.awaitTermination(1, TimeUnit.MINUTES)) {
            // Keep waiting; a single log file never takes this long, so this only loops under extreme load.
        }
    }

    private static PreparedLog prepare(LogCandidate candidate) throws IOException {
        ParsedLog parsed;
        try (BufferedReader reader = open(candidate)) {
            parsed = LogParser.parse(reader);
        }
        LogDates.Resolved resolved = LogDates.resolve(candidate.fileName(), candidate.lastModified());

        List<LocalDateTime> times = new ArrayList<>(parsed.entries().size());
        List<String> messages = new ArrayList<>(parsed.entries().size());
        for (ParsedLog.Entry entry : parsed.entries()) {
            times.add(LocalDateTime.of(resolved.date(), entry.time()));
            messages.add(entry.message());
        }
        LocalDateTime firstLineTime = parsed.firstLineTime() == null
            ? null : LocalDateTime.of(resolved.date(), parsed.firstLineTime());
        LocalDateTime lastLineTime = parsed.lastLineTime() == null
            ? null : LocalDateTime.of(resolved.date(), parsed.lastLineTime());
        return new PreparedLog(candidate.fileName(), candidate.sourceKind(), candidate.sourcePath(),
            candidate.entryPath(), resolved.date(), resolved.source(), parsed.minecraftVersion(),
            candidate.lastModified(), times, messages, parsed.resourceManagerReloaded(),
            firstLineTime, lastLineTime);
    }

    private static BufferedReader open(LogCandidate candidate) throws IOException {
        InputStream stream = new ByteArrayInputStream(candidate.content());
        if (candidate.fileName().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            stream = new GZIPInputStream(stream);
        }
        byte[] bytes = stream.readAllBytes();
        // Minecraft writes logs in UTF-8, but older clients on Windows produced bytes in the system code page
        // (Windows-1252). Decoding those bytes as UTF-8 would silently turn every non-ASCII byte, including the
        // section sign that introduces formatting codes, into the replacement character, so the file is only
        // treated as UTF-8 if it actually is valid UTF-8.
        String text = decode(bytes);
        return new BufferedReader(new StringReader(text), 1 << 16);
    }

    private static String decode(byte[] bytes) {
        CharsetDecoder strictUtf8 = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return strictUtf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, WINDOWS_1252);
        }
    }

    private static ChatEntry readEntry(ResultSet result, Map<String, LogFile> fileCache) throws SQLException {
        // Many consecutive rows come from the same file, so resolving metadata once per file avoids rebuilding the
        // same record thousands of times.
        String cacheKey = result.getString(3) + "\u0000" + result.getString(4);
        LogFile file = fileCache.get(cacheKey);
        if (file == null) {
            file = readLogFile(result, 1);
            fileCache.put(cacheKey, file);
        }
        LocalDateTime timestamp = result.getTimestamp(12).toLocalDateTime();
        return new ChatEntry(file, timestamp, result.getInt(13), result.getString(14));
    }

    private static LogFile readLogFile(ResultSet result, int offset) throws SQLException {
        return new LogFile(
            result.getString(offset),
            SourceKind.valueOf(result.getString(offset + 1)),
            result.getString(offset + 2),
            result.getString(offset + 3),
            result.getDate(offset + 4).toLocalDate(),
            DateSource.valueOf(result.getString(offset + 5)),
            result.getString(offset + 6),
            optionalTimestamp(result, offset + 7),
            optionalTimestamp(result, offset + 8),
            optionalTimestamp(result, offset + 9),
            result.getLong(offset + 10));
    }

    private static Optional<LocalDateTime> optionalTimestamp(ResultSet result, int column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? Optional.empty() : Optional.of(timestamp.toLocalDateTime());
    }

    /// The file this store is backed by, or empty for an in memory store.
    public Optional<Path> databasePath() {
        return Optional.ofNullable(databasePath);
    }

    /// Imports every log file found below `directory` with [ImportOptions#defaults()].
    public ImportResult importDirectory(Path directory) {
        return importDirectory(directory, ImportOptions.defaults());
    }

    /// Imports every log file found below `directory`.
    ///
    /// @param directory the directory to walk; also the root that [ImportOptions#pathMatcher()] and
    ///                  [LogFile#entryPath()] are relative to
    /// @throws LogDataException if `directory` is not a directory, or the database rejects the writes
    public ImportResult importDirectory(Path directory, ImportOptions options) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(options, "options");
        return runImport(options, discovery -> discovery.discoverDirectory(directory));
    }

    /// Imports every log file inside `archive` with [ImportOptions#defaults()].
    public ImportResult importArchive(Path archive) {
        return importArchive(archive, ImportOptions.defaults());
    }

    /// Imports every log file inside `archive`. Zip, 7z, tar and tar.gz archives are supported.
    ///
    /// @param archive the archive to read; [LogFile#entryPath()] of the results is relative to its root
    /// @throws LogDataException if `archive` is not a file, or the database rejects the writes
    public ImportResult importArchive(Path archive, ImportOptions options) {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(options, "options");
        return runImport(options, discovery -> discovery.discoverArchive(archive));
    }

    /// Discovers candidates on a background thread, parses them on a pool and writes them on the calling thread.
    ///
    /// The writer runs on the calling thread because DuckDB's appender is confined to the thread that created it, and
    /// the store is opened by the caller. Discovery is pushed to its own thread so that reading archives overlaps with
    /// writing rather than serialising behind it.
    private ImportResult runImport(ImportOptions options, Consumer<LogDiscovery> walk) {
        BlockingQueue<PreparedLog> queue = new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY);
        List<ImportResult.Failure> parseFailures = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger empty = new AtomicInteger();

        try (LogWriter writer = new LogWriter(connection)) {
            var failureRef = new AtomicReference<RuntimeException>();
            ExecutorService parsers = Executors.newFixedThreadPool(options.parallelism());
            LogDiscovery discovery = new LogDiscovery(options, candidate -> {
                if (options.skipAlreadyImported()
                    && writer.isAlreadyImported(candidate.sourcePath(), candidate.entryPath())) {
                    skipped.incrementAndGet();
                    return;
                }
                parsers.execute(() -> {
                    try {
                        PreparedLog prepared = prepare(candidate);
                        if (prepared.messages().isEmpty() && !prepared.resourceManagerReloaded()) {
                            empty.incrementAndGet();
                            return;
                        }
                        queue.put(prepared);
                    } catch (IOException e) {
                        parseFailures.add(new ImportResult.Failure(candidate.entryPath(),
                            "could not parse: " + e.getMessage()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });

            Thread discoverer = new Thread(() -> {
                try {
                    walk.accept(discovery);
                } catch (RuntimeException e) {
                    failureRef.set(e);
                } finally {
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
                }
            } finally {
                // Drain whatever is still queued so no parsing thread stays blocked on a full queue if writing failed.
                drain(queue, discoverer);
            }

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

    /// Stores a single chat line captured from a running game, stamped with the current time.
    ///
    /// Live entries are grouped into one synthetic [LogFile] per day and Minecraft version, whose
    /// [LogFile#sourceKind()] is [SourceKind#LIVE], so they sit alongside imported logs in every query. A line that
    /// repeats the timestamp and text of an entry already stored is dropped, which keeps a live capture running next
    /// to the game from duplicating what the log file import will later pick up.
    ///
    /// @param minecraftVersion the version of the running game
    /// @param message          the chat line as the game rendered it; formatting codes are stripped like on import
    /// @return `true` if the entry was stored, `false` if it was dropped as a duplicate
    /// @throws LogDataException if the entry cannot be written
    public boolean addLiveEntry(String minecraftVersion, String message) {
        return addLiveEntry(minecraftVersion, message, LocalDateTime.now());
    }

    /// Stores a single chat line captured from a running game at an explicit time.
    ///
    /// @see #addLiveEntry(String, String)
    public boolean addLiveEntry(String minecraftVersion, String message, LocalDateTime timestamp) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");

        // Whole seconds only, so a live entry and the same line read back from the log file collide as intended.
        LocalDateTime stamp = timestamp.withNano(0);
        String stripped = FormattingCodes.strip(message);
        try {
            return writeLiveEntry(minecraftVersion, stripped, stamp);
        } catch (SQLException e) {
            throw new LogDataException("could not store live chat entry", e);
        }
    }

    private boolean writeLiveEntry(String minecraftVersion, String message, LocalDateTime timestamp)
        throws SQLException {
        try (PreparedStatement duplicate = connection.prepareStatement(
            "SELECT 1 FROM chat_entry WHERE entry_time = ? AND message = ? LIMIT 1")) {
            duplicate.setTimestamp(1, Timestamp.valueOf(timestamp));
            duplicate.setString(2, message);
            try (ResultSet result = duplicate.executeQuery()) {
                if (result.next()) return false;
            }
        }

        LocalDate date = timestamp.toLocalDate();
        String entryPath = "live/" + date + "/" + minecraftVersion;
        long fileId;
        int lineIndex;
        try (PreparedStatement existing = connection.prepareStatement(
            "SELECT id, entry_count FROM log_file WHERE source_path = ? AND entry_path = ?")) {
            existing.setString(1, LIVE_SOURCE_PATH);
            existing.setString(2, entryPath);
            try (ResultSet result = existing.executeQuery()) {
                if (result.next()) {
                    fileId = result.getLong(1);
                    lineIndex = result.getInt(2);
                } else {
                    fileId = nextFileId();
                    lineIndex = 0;
                    insertLiveFile(fileId, minecraftVersion, date, entryPath);
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_entry (file_id, line_index, entry_time, message) VALUES (?, ?, ?, ?)")) {
            insert.setLong(1, fileId);
            insert.setInt(2, lineIndex);
            insert.setTimestamp(3, Timestamp.valueOf(timestamp));
            insert.setString(4, message);
            insert.execute();
        }
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET
                entry_count = entry_count + 1,
                first_entry_time = least(coalesce(first_entry_time, ?), ?),
                last_entry_time = greatest(coalesce(last_entry_time, ?), ?)
            WHERE id = ?""")) {
            Timestamp stamp = Timestamp.valueOf(timestamp);
            update.setTimestamp(1, stamp);
            update.setTimestamp(2, stamp);
            update.setTimestamp(3, stamp);
            update.setTimestamp(4, stamp);
            update.setLong(5, fileId);
            update.execute();
        }
        return true;
    }

    private void insertLiveFile(long fileId, String minecraftVersion, LocalDate date, String entryPath)
        throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO log_file (id, file_name, source_kind, source_path, entry_path, log_date, date_source,
                                  minecraft_version, last_modified, first_entry_time, last_entry_time, entry_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, 0)""")) {
            insert.setLong(1, fileId);
            insert.setString(2, date + "-live.log");
            insert.setString(3, SourceKind.LIVE.name());
            insert.setString(4, LIVE_SOURCE_PATH);
            insert.setString(5, entryPath);
            insert.setDate(6, java.sql.Date.valueOf(date));
            insert.setString(7, DateSource.FILE_NAME.name());
            insert.setString(8, minecraftVersion);
            insert.execute();
        }
    }

    private long nextFileId() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT coalesce(max(id) + 1, 0) FROM log_file")) {
            result.next();
            return result.getLong(1);
        }
    }

    /// Removes entries that share a timestamp and message with another entry, keeping one of them.
    ///
    /// Imports do this automatically; call it directly only after writing through some other path.
    ///
    /// @return the number of removed entries
    public long deduplicate() {
        try (LogWriter writer = new LogWriter(connection)) {
            return writer.deduplicate();
        } catch (SQLException e) {
            throw new LogDataException("could not deduplicate chat entries", e);
        }
    }

    /// Returns every entry matching `query`, resolved into records with their log file attached.
    ///
    /// @throws LogDataException if the query is rejected, e.g. because its regex is malformed
    public List<ChatEntry> query(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        List<ChatEntry> entries = new ArrayList<>();
        forEach(query, entries::add);
        return entries;
    }

    /// Returns every stored entry, oldest first. Convenience for `query(ChatQuery.all())`.
    public List<ChatEntry> allEntries() {
        return query(ChatQuery.all());
    }

    /// Returns entries whose message contains `substring`, compared case insensitively.
    public List<ChatEntry> search(String substring) {
        return query(ChatQuery.all().withSubstring(substring));
    }

    /// Returns entries whose timestamp lies in `[from, to)`. Either bound may be `null` to leave that side open.
    public List<ChatEntry> inRange(LocalDateTime from, LocalDateTime to) {
        return query(ChatQuery.all().withRange(from, to));
    }

    /// Streams matching entries to `consumer` without materialising them all in memory, for result sets that are too
    /// large to hold at once.
    ///
    /// @throws LogDataException if the query is rejected, e.g. because its regex is malformed
    public void forEach(ChatQuery query, Consumer<ChatEntry> consumer) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(consumer, "consumer");
        QueryBuilder builder = QueryBuilder.build(query);
        Map<String, LogFile> fileCache = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(builder.sql())) {
            builder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    consumer.accept(readEntry(result, fileCache));
                }
            }
        } catch (SQLException e) {
            throw new LogDataException("could not run query " + query, e);
        }
    }

    /// Counts entries matching `query`, ignoring its context lines, ordering and limit.
    public long count(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        QueryBuilder builder = QueryBuilder.buildCount(query);
        try (PreparedStatement statement = connection.prepareStatement(builder.sql())) {
            builder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new LogDataException("could not count entries for " + query, e);
        }
    }

    /// Returns metadata for every imported log file, ordered by date.
    public List<LogFile> logFiles() {
        List<LogFile> files = new ArrayList<>();
        String sql = """
            SELECT file_name, source_kind, source_path, entry_path, log_date, date_source, minecraft_version,
                   last_modified, first_entry_time, last_entry_time, entry_count
            FROM log_file ORDER BY log_date, entry_path""";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                files.add(readLogFile(result, 1));
            }
        } catch (SQLException e) {
            throw new LogDataException("could not read log file metadata", e);
        }
        return files;
    }

    /// Reclaims space and rewrites the database file compactly. Worth calling after a large import.
    public void compact() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CHECKPOINT");
        } catch (SQLException e) {
            throw new LogDataException("could not compact the database", e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new LogDataException("could not close the log database", e);
        }
    }
}
