package me.wolfii.allthelogs.data.store;

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
import java.util.Objects;

/**
 * Captures chat lines from a running Minecraft client into a {@link LogSource.Session} log.
 */
public final class SessionCapture {

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
     * never dropped as duplicates; a later file import that repeats the same second and text is the copy
     * that {@link LogWriter#deduplicate()} removes. Legacy {@code §} codes are
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
        LocalDateTime stamp = timestamp.truncatedTo(ChronoUnit.MILLIS);
        String text;
        long[] packed;
        if (formatting == null) {
            FormattingCodes.Parsed parsed = FormattingCodes.parse(message);
            text = parsed.text();
            packed = parsed.formatting();
        } else {
            text = message;
            packed = formatting.length == 0 ? null : formatting;
        }
        try {
            return writeEntry(text, packed, stamp);
        } catch (SQLException e) {
            throw new LogDataException("could not store client chat entry", e);
        }
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
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_entry (file_id, line_index, entry_time, message, formatting, minecraft_user, server_or_world) VALUES (?, ?, ?, ?, CAST(? AS BIGINT[]), ?, ?)")) {
            insert.setLong(1, sessionFileId);
            insert.setInt(2, sessionLineIndex);
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
            insert.execute();
        }
        sessionLineIndex++;
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET
                entry_count = entry_count + 1,
                end_time = greatest(end_time, ?)
            WHERE id = ?""")) {
            update.setTimestamp(1, Timestamp.valueOf(timestamp));
            update.setLong(2, sessionFileId);
            update.execute();
        }
        return true;
    }

    private long nextFileId() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT coalesce(max(id) + 1, 0) FROM log_file")) {
            result.next();
            return result.getLong(1);
        }
    }
}
