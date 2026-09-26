package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.api.PendingImportMessage;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.duckdb.DuckDBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Captures chat lines from a running Minecraft client into a {@link LogSource.Session} log.
 */
public final class SessionCapture {
    private static final String INSERT_ENTRY = """
        INSERT INTO chat_entry (file_id, line_index, entry_time, message, formatting, minecraft_user, server_or_world)
        VALUES (?, ?, ?, ?, CAST(? AS BIGINT[]), ?, ?)""";

    private DuckDBConnection connection;
    private long sessionFileId = -1;
    private int sessionLineIndex;
    private String currentPlace;
    private String currentUser;

    public SessionCapture(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * Points this capture at a replacement connection after the store compacted the database file.
     * Session identity ({@code sessionFileId} / line index) is unchanged because those rows were copied.
     */
    public void attach(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * Starts a capture session at {@code startedAt} (milliseconds) and returns the created log, which carries a
     * unique {@link LogSource.Session#id()}. {@code minecraftUser} is stored on the session log and on later
     * chat lines. {@code serverOrWorld} is remembered for those lines only (a session can visit several).
     *
     * @throws LogDataException if the session cannot be written
     */
    public ChatLog start(String minecraftVersion, LocalDateTime startedAt, String minecraftUser) {
        return start(minecraftVersion, startedAt, minecraftUser, null);
    }

    public ChatLog start(String minecraftVersion, LocalDateTime startedAt, String minecraftUser, String serverOrWorld) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(startedAt, "startedAt");
        LocalDateTime start = startedAt.truncatedTo(ChronoUnit.MILLIS);
        String sessionId = SessionMarker.newId();
        try {
            long fileId = nextFileId();
            LocalDate date = start.toLocalDate();
            String entryPath = SessionMarker.entryPath(sessionId);
            Timestamp timestamp = Timestamp.valueOf(start);
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO log_file (id, file_name, source_kind, source_path, entry_path, log_date,
                                      minecraft_version, start_time, end_time, entry_count, minecraft_user)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""")) {
                insert.setLong(1, fileId);
                insert.setString(2, "");
                insert.setString(3, SourceKind.SESSION.name());
                insert.setString(4, StoredSources.SESSION_PATH);
                insert.setString(5, entryPath);
                insert.setDate(6, Date.valueOf(date));
                insert.setString(7, minecraftVersion);
                insert.setTimestamp(8, timestamp);
                insert.setTimestamp(9, timestamp);
                if (minecraftUser == null) {
                    insert.setNull(10, Types.VARCHAR);
                } else {
                    insert.setString(10, minecraftUser);
                }
                insert.execute();
            }
            sessionFileId = fileId;
            sessionLineIndex = 0;
            currentUser = minecraftUser;
            currentPlace = serverOrWorld;
            return new ChatLog(new LogSource.Session(sessionId), date, minecraftVersion, start, start, minecraftUser);
        } catch (SQLException e) {
            throw new LogDataException("could not start a client session", e);
        }
    }

    /**
     * Stores a chat line in the current session. Timestamps are truncated to milliseconds. Live rows are
     * never dropped as duplicates, including two identical lines a few seconds apart. A later file import
     * that repeats text already stored before that import, within {@link EntryMatch#WINDOW_SECONDS}, is
     * the copy that {@link LogWriter#deduplicate()} removes. Legacy {@code §} codes are
     * stripped like on file import; {@code formatting} is stored as packed runs, or parsed from the
     * message when {@code null}.
     *
     * @return {@code true} if stored
     * @throws LogDataException if no session is active, or the entry cannot be written
     */
    public boolean importMessage(String message, LocalDateTime timestamp) {
        return importMessage(message, null, timestamp);
    }

    public boolean importMessage(String message, long[] formatting, LocalDateTime timestamp) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
        requireActiveSession();
        StoredLine line = resolve(message, formatting, timestamp);
        try {
            return writeEntry(line.text(), line.formatting(), line.timestamp());
        } catch (SQLException e) {
            throw new LogDataException("could not store client chat entry", e);
        }
    }

    /**
     * Stores every queued live line in one transaction. Each line is stamped with its own player and
     * place, then appended in list order. An empty list does nothing, including when no session is active.
     *
     * @throws LogDataException if no session is active, or the entries cannot be written
     */
    public void importMessages(List<PendingImportMessage> messages) {
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) return;
        requireActiveSession();
        boolean open = false;
        try {
            try (Statement begin = connection.createStatement()) {
                begin.execute("BEGIN TRANSACTION");
            }
            open = true;
            int nextLine = sessionLineIndex;
            LocalDateTime latest = null;
            try (PreparedStatement insert = connection.prepareStatement(INSERT_ENTRY)) {
                for (PendingImportMessage pending : messages) {
                    Objects.requireNonNull(pending, "messages");
                    stampLiveCapture(pending.minecraftUser(), pending.serverOrWorld());
                    StoredLine line = resolve(pending.text(), pending.formatting(), pending.capturedAt());
                    bindEntry(insert, nextLine, line.text(), line.formatting(), line.timestamp());
                    insert.execute();
                    if (latest == null || line.timestamp().isAfter(latest)) latest = line.timestamp();
                    nextLine++;
                }
            }
            touchSession(latest, nextLine - sessionLineIndex);
            try (Statement commit = connection.createStatement()) {
                commit.execute("COMMIT");
            }
            open = false;
            sessionLineIndex = nextLine;
        } catch (SQLException e) {
            throw new LogDataException("could not store client chat entries", e);
        } finally {
            if (open) rollback();
        }
    }

    private static StoredLine resolve(String message, long[] formatting, LocalDateTime timestamp) {
        LocalDateTime stamp = timestamp.truncatedTo(ChronoUnit.MILLIS);
        if (formatting == null) {
            FormattingCodes.Parsed parsed = FormattingCodes.parse(message);
            return new StoredLine(parsed.text(), parsed.formatting(), stamp);
        }
        long[] packed = formatting.length == 0 ? null : formatting;
        return new StoredLine(message, packed, stamp);
    }

    private record StoredLine(String text, long[] formatting, LocalDateTime timestamp) {
    }

    /**
     * Updates the current session's end time without storing a chat line. Truncated to milliseconds;
     * an earlier timestamp than the one already stored is ignored.
     *
     * @throws LogDataException if no session is active, or the update cannot be written
     */
    public void updateEndTime(LocalDateTime timestamp) {
        Objects.requireNonNull(timestamp, "timestamp");
        requireActiveSession();
        LocalDateTime stamp = timestamp.truncatedTo(ChronoUnit.MILLIS);
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET end_time = greatest(end_time, ?)
            WHERE id = ?""")) {
            update.setTimestamp(1, Timestamp.valueOf(stamp));
            update.setLong(2, sessionFileId);
            update.execute();
        } catch (SQLException e) {
            throw new LogDataException("could not update the session end time", e);
        }
    }

    /**
     * Remembers {@code serverOrWorld} for later chat lines in this session. {@code null} means the player
     * left; later lines are stored without a server or world until the next non-null update.
     */
    public void updatePlace(String serverOrWorld) {
        requireActiveSession();
        currentPlace = serverOrWorld;
    }

    /**
     * Stamps the player and server/world read from the Minecraft client at live-capture time.
     * A blank or null username keeps the last known player; {@code serverOrWorld} is always applied,
     * including {@code null} after leave.
     */
    public void stampLiveCapture(String minecraftUser, String serverOrWorld) {
        requireActiveSession();
        if (minecraftUser != null && !minecraftUser.isBlank()) {
            currentUser = minecraftUser;
        }
        currentPlace = serverOrWorld == null || serverOrWorld.isBlank() ? null : serverOrWorld;
    }

    private void requireActiveSession() {
        if (sessionFileId < 0) {
            throw new LogDataException("no client session is active; call startSession first");
        }
    }

    private boolean writeEntry(String message, long[] formatting, LocalDateTime timestamp) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(INSERT_ENTRY)) {
            LocalDateTime stamp = bindEntry(insert, sessionLineIndex, message, formatting, timestamp);
            insert.execute();
            sessionLineIndex++;
            touchSession(stamp, 1);
        }
        return true;
    }

    private LocalDateTime bindEntry(PreparedStatement insert, int lineIndex, String message, long[] formatting,
                                    LocalDateTime timestamp) throws SQLException {
        insert.setLong(1, sessionFileId);
        insert.setInt(2, lineIndex);
        insert.setTimestamp(3, Timestamp.valueOf(timestamp));
        insert.setString(4, message);
        insert.setString(5, PackedFormatting.toSqlLiteral(formatting));
        if (currentUser == null) {
            insert.setNull(6, Types.VARCHAR);
        } else {
            insert.setString(6, currentUser);
        }
        if (currentPlace == null) {
            insert.setNull(7, Types.VARCHAR);
        } else {
            insert.setString(7, currentPlace);
        }
        return timestamp;
    }

    private void touchSession(LocalDateTime stamp, int addedEntries) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET
                entry_count = entry_count + ?,
                end_time = greatest(end_time, ?)
            WHERE id = ?""")) {
            update.setInt(1, addedEntries);
            update.setTimestamp(2, Timestamp.valueOf(stamp));
            update.setLong(3, sessionFileId);
            update.execute();
        }
    }

    private void rollback() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException ignored) {
        }
    }

    private long nextFileId() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT coalesce(max(id) + 1, 0) FROM log_file")) {
            result.next();
            return result.getLong(1);
        }
    }
}
