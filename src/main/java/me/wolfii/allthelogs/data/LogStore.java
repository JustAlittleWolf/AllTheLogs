package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.internal.FormattingCodes;
import me.wolfii.allthelogs.data.internal.LogCandidate;
import me.wolfii.allthelogs.data.internal.LogDates;
import me.wolfii.allthelogs.data.internal.LogDiscovery;
import me.wolfii.allthelogs.data.internal.LogParser;
import me.wolfii.allthelogs.data.internal.LogWriter;
import me.wolfii.allthelogs.data.internal.ParsedLog;
import me.wolfii.allthelogs.data.internal.PreparedLog;
import me.wolfii.allthelogs.data.internal.QueryBuilder;
import me.wolfii.allthelogs.data.internal.Schema;
import me.wolfii.allthelogs.data.internal.SourceKind;
import org.duckdb.DuckDBConnection;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/// Imports Minecraft chat logs into a single portable database file and queries them back as plain Java objects.
///
/// The store is backed by an embedded DuckDB database, which needs no server, works on every major operating system
/// and keeps everything in the one file passed to [#open(Path)]. Callers never see the schema: imports take file
/// system paths, queries take a [ChatQuery] and return [ChatEntry] records with their [ChatLog] already resolved.
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
    /// Recorded as the source path of the rows that hold a running client session.
    private static final String SESSION_SOURCE_PATH = "<session>";
    /// Sentinel that tells the writer loop that no more logs are coming.
    private static final PreparedLog END_OF_STREAM = new PreparedLog(
        "", SourceKind.DIRECTORY, "", "", LocalDate.EPOCH, "", List.of(), List.of(),
        false, null, null);

    private final DuckDBConnection connection;
    private final Path databasePath;
    /// File id of the current client session, or `-1` when none is active.
    private long sessionFileId = -1;
    /// Next line index to assign in the current session.
    private int sessionLineIndex;

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
    static LogStore openInMemory() {
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

    private static PreparedLog prepare(LogCandidate candidate, ZoneId timezone) throws IOException {
        ParsedLog parsed;
        try (BufferedReader reader = open(candidate)) {
            parsed = LogParser.parse(reader);
        }
        LocalDate date = LogDates.resolve(candidate.fileName(), candidate.lastModified(), timezone);

        List<LocalDateTime> times = new ArrayList<>(parsed.entries().size());
        List<String> messages = new ArrayList<>(parsed.entries().size());
        for (ParsedLog.Entry entry : parsed.entries()) {
            times.add(LogDates.toSystemLocal(date, entry.time(), timezone));
            messages.add(entry.message());
        }
        LocalDateTime firstLineTime = LogDates.toSystemLocal(date, parsed.firstLineTime(), timezone);
        LocalDateTime lastLineTime = LogDates.toSystemLocal(date, parsed.lastLineTime(), timezone);
        return new PreparedLog(candidate.fileName(), candidate.sourceKind(), candidate.sourcePath(),
            candidate.entryPath(), date, parsed.minecraftVersion(),
            times, messages, parsed.resourceManagerReloaded(),
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

    private static ChatEntry readEntry(ResultSet result, Map<String, ChatLog> logCache) throws SQLException {
        // Many consecutive rows come from the same log, so resolving metadata once per log avoids rebuilding the
        // same record thousands of times.
        String cacheKey = result.getString(3) + "\u0000" + result.getString(4);
        ChatLog log = logCache.get(cacheKey);
        if (log == null) {
            log = readChatLog(result, 1);
            logCache.put(cacheKey, log);
        }
        LocalDateTime timestamp = result.getTimestamp(10).toLocalDateTime();
        return new ChatEntry(log, timestamp, result.getInt(11), result.getString(12));
    }

    private static ChatLog readChatLog(ResultSet result, int offset) throws SQLException {
        return new ChatLog(
            readLogSource(result, offset),
            result.getDate(offset + 4).toLocalDate(),
            result.getString(offset + 5),
            result.getTimestamp(offset + 6).toLocalDateTime(),
            result.getTimestamp(offset + 7).toLocalDateTime(),
            result.getLong(offset + 8));
    }

    private static LogSource readLogSource(ResultSet result, int offset) throws SQLException {
        String fileName = result.getString(offset);
        String kind = result.getString(offset + 1);
        String path = result.getString(offset + 2);
        String entryPath = result.getString(offset + 3);
        SourceKind sourceKind;
        try {
            sourceKind = SourceKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new SQLException("unknown source kind: " + kind, e);
        }
        return switch (sourceKind) {
            case DIRECTORY -> new LogSource.Directory(fileName, path, entryPath);
            case ARCHIVE -> new LogSource.Archive(fileName, path, entryPath);
            case SESSION -> new LogSource.Session();
        };
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
    ///                  [LogSource.Directory#entryPath()] are relative to
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
    /// @param archive the archive to read; [LogSource.Archive#entryPath()] of the results is relative to its root
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
        List<ImportResult.Failure> parseFailures = Collections.synchronizedList(new ArrayList<>());
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
                        PreparedLog prepared = prepare(candidate, options.timezone());
                        if (prepared.firstLineTime() == null || prepared.lastLineTime() == null
                            || (prepared.messages().isEmpty() && !prepared.resourceManagerReloaded())) {
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

    /// Starts a capture session for a running Minecraft client and creates a [ChatLog] for it.
    ///
    /// Chat lines imported with [#importSessionMessage(String)] are stored against this log. Its
    /// [ChatLog#firstEntryTime()] is the session start; [#importSessionMessage(String, LocalDateTime)] and
    /// [#updateSessionLastEntryTime(LocalDateTime)] update [ChatLog#lastEntryTime()] as the session continues.
    /// Starting another session leaves the previous log in place and switches subsequent imports to the new one.
    ///
    /// The log's [ChatLog#source()] is a [LogSource.Session], since the lines were captured from the running client
    /// rather than read from a directory or archive.
    ///
    /// @param minecraftVersion the version of the running game
    /// @return the created chat log, with no entries yet
    /// @throws LogDataException if the session cannot be written
    public ChatLog startSession(String minecraftVersion) {
        return startSession(minecraftVersion, LocalDateTime.now());
    }

    /// Starts a capture session at an explicit time.
    ///
    /// @see #startSession(String)
    public ChatLog startSession(String minecraftVersion, LocalDateTime startedAt) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(startedAt, "startedAt");
        LocalDateTime start = startedAt.withNano(0);
        try {
            return insertSessionFile(minecraftVersion, start);
        } catch (SQLException e) {
            throw new LogDataException("could not start a client session", e);
        }
    }

    /// Imports a single chat line from the running client into the current session, stamped with the current time.
    ///
    /// A line that repeats the timestamp and text of an entry already stored is dropped, which keeps a capture
    /// running next to the game from duplicating what a later log file import will pick up.
    ///
    /// @param message the chat line as the game rendered it; formatting codes are stripped like on import
    /// @return `true` if the entry was stored, `false` if it was dropped as a duplicate
    /// @throws LogDataException if no session is active, or the entry cannot be written
    public boolean importSessionMessage(String message) {
        return importSessionMessage(message, LocalDateTime.now());
    }

    /// Imports a single chat line from the running client into the current session at an explicit time.
    ///
    /// @see #importSessionMessage(String)
    public boolean importSessionMessage(String message, LocalDateTime timestamp) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
        requireActiveSession();

        // Whole seconds only, so a client entry and the same line read back from the log file collide as intended.
        LocalDateTime stamp = timestamp.withNano(0);
        String stripped = FormattingCodes.strip(message);
        try {
            return writeSessionEntry(stripped, stamp);
        } catch (SQLException e) {
            throw new LogDataException("could not store client chat entry", e);
        }
    }

    /// Updates [ChatLog#lastEntryTime()] of the current session to the current time, without storing a chat line.
    public void updateSessionLastEntryTime() {
        updateSessionLastEntryTime(LocalDateTime.now());
    }

    /// Updates [ChatLog#lastEntryTime()] of the current session, without storing a chat line.
    ///
    /// Whole seconds only, matching [#importSessionMessage(String, LocalDateTime)]. If `timestamp` is earlier than
    /// the time already stored, the existing last entry time is kept.
    ///
    /// @throws LogDataException if no session is active, or the update cannot be written
    public void updateSessionLastEntryTime(LocalDateTime timestamp) {
        Objects.requireNonNull(timestamp, "timestamp");
        requireActiveSession();
        LocalDateTime stamp = timestamp.withNano(0);
        try {
            writeSessionLastEntryTime(stamp);
        } catch (SQLException e) {
            throw new LogDataException("could not update the session last entry time", e);
        }
    }

    private void requireActiveSession() {
        if (sessionFileId < 0) {
            throw new LogDataException("no client session is active; call startSession first");
        }
    }

    private ChatLog insertSessionFile(String minecraftVersion, LocalDateTime startedAt) throws SQLException {
        long fileId = nextFileId();
        LocalDate date = startedAt.toLocalDate();
        String entryPath = "session/" + fileId;
        Timestamp start = Timestamp.valueOf(startedAt);
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO log_file (id, file_name, source_kind, source_path, entry_path, log_date,
                                  minecraft_version, first_entry_time, last_entry_time, entry_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""")) {
            insert.setLong(1, fileId);
            insert.setString(2, "");
            insert.setString(3, SourceKind.SESSION.name());
            insert.setString(4, SESSION_SOURCE_PATH);
            insert.setString(5, entryPath);
            insert.setDate(6, Date.valueOf(date));
            insert.setString(7, minecraftVersion);
            insert.setTimestamp(8, start);
            insert.setTimestamp(9, start);
            insert.execute();
        }
        sessionFileId = fileId;
        sessionLineIndex = 0;
        return new ChatLog(new LogSource.Session(), date, minecraftVersion, startedAt, startedAt, 0);
    }

    private boolean writeSessionEntry(String message, LocalDateTime timestamp) throws SQLException {
        try (PreparedStatement duplicate = connection.prepareStatement(
            "SELECT 1 FROM chat_entry WHERE entry_time = ? AND message = ? LIMIT 1")) {
            duplicate.setTimestamp(1, Timestamp.valueOf(timestamp));
            duplicate.setString(2, message);
            try (ResultSet result = duplicate.executeQuery()) {
                if (result.next()) return false;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_entry (file_id, line_index, entry_time, message) VALUES (?, ?, ?, ?)")) {
            insert.setLong(1, sessionFileId);
            insert.setInt(2, sessionLineIndex);
            insert.setTimestamp(3, Timestamp.valueOf(timestamp));
            insert.setString(4, message);
            insert.execute();
        }
        sessionLineIndex++;
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET
                entry_count = entry_count + 1,
                last_entry_time = greatest(last_entry_time, ?)
            WHERE id = ?""")) {
            update.setTimestamp(1, Timestamp.valueOf(timestamp));
            update.setLong(2, sessionFileId);
            update.execute();
        }
        return true;
    }

    private void writeSessionLastEntryTime(LocalDateTime timestamp) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET last_entry_time = greatest(last_entry_time, ?)
            WHERE id = ?""")) {
            update.setTimestamp(1, Timestamp.valueOf(timestamp));
            update.setLong(2, sessionFileId);
            update.execute();
        }
    }

    private long nextFileId() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT coalesce(max(id) + 1, 0) FROM log_file")) {
            result.next();
            return result.getLong(1);
        }
    }

    /// Returns every entry matching `query`, resolved into records with their chat log attached.
    ///
    /// @throws LogDataException if the query is rejected, e.g. because its regex is malformed
    public List<ChatEntry> query(ChatQuery query) {
        Objects.requireNonNull(query, "query");
        QueryBuilder builder = QueryBuilder.build(query);
        List<ChatEntry> entries = new ArrayList<>();
        Map<String, ChatLog> logCache = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(builder.sql())) {
            builder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(readEntry(result, logCache));
                }
            }
        } catch (SQLException e) {
            throw new LogDataException("could not run query " + query, e);
        }
        return entries;
    }

    /// Returns every stored entry, oldest first. Convenience for `query(ChatQuery.all())`.
    public List<ChatEntry> logEntries() {
        return query(ChatQuery.all());
    }

    /// Returns every imported chat log, ordered by date.
    public List<ChatLog> chatLogs() {
        List<ChatLog> logs = new ArrayList<>();
        String sql = """
            SELECT file_name, source_kind, source_path, entry_path, log_date, minecraft_version,
                   first_entry_time, last_entry_time, entry_count
            FROM log_file ORDER BY log_date, entry_path""";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                logs.add(readChatLog(result, 1));
            }
        } catch (SQLException e) {
            throw new LogDataException("could not read chat log metadata", e);
        }
        return logs;
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
