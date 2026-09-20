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
     * Starts a capture session at {@code startedAt} (whole seconds) and returns the created log, which carries a
     * unique {@link LogSource.Session#id()}. {@code minecraftUser} and {@code serverPlace} are stored when known,
     * or {@code null}.
     *
     * @throws LogDataException if the session cannot be written
     */
    public ChatLog start(String minecraftVersion, LocalDateTime startedAt, String minecraftUser) {
        return start(minecraftVersion, startedAt, minecraftUser, null);
    }

    public ChatLog start(String minecraftVersion, LocalDateTime startedAt, String minecraftUser, String serverPlace) {
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
                                      minecraft_version, start_time, end_time, entry_count, minecraft_user,
                                      server_place)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)""")) {
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
                if (serverPlace == null) {
                    insert.setNull(11, Types.VARCHAR);
                } else {
                    insert.setString(11, serverPlace);
                }
                insert.execute();
            }
            sessionFileId = fileId;
            sessionLineIndex = 0;
            currentPlace = serverPlace;
            return new ChatLog(new LogSource.Session(sessionId), date, minecraftVersion, start, start, minecraftUser,
                serverPlace);
        } catch (SQLException e) {
            throw new LogDataException("could not start a client session", e);
        }
    }

    /**
     * Stores a chat line in the current session. Timestamps are truncated to whole seconds so a
     * later file import of the same line can be recognised as a duplicate. Legacy {@code §} codes are
     * stripped like on file import; {@code formatting} is stored as packed runs, or parsed from the
     * message when {@code null}.
     *
     * @return {@code true} if stored, {@code false} if dropped as a duplicate
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
     * Updates the current session's end time without storing a chat line. Whole seconds only;
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
     * Stores {@code serverPlace} on the current session. {@code null} means the player left;
     * later chat lines are stored without a place until the next non-null update. The session
     * log keeps the last non-null place for catalog metadata.
     */
    public void updatePlace(String serverPlace) {
        requireActiveSession();
        currentPlace = serverPlace;
        if (serverPlace == null) return;
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE log_file SET server_place = ?
            WHERE id = ?""")) {
            update.setString(1, serverPlace);
            update.setLong(2, sessionFileId);
            update.execute();
        } catch (SQLException e) {
            throw new LogDataException("could not update the session server or world", e);
        }
    }

    private void requireActiveSession() {
        if (sessionFileId < 0) {
            throw new LogDataException("no client session is active; call startSession first");
        }
    }

    private boolean writeEntry(String message, long[] formatting, LocalDateTime timestamp) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_entry (file_id, line_index, entry_time, message, formatting, server_place) VALUES (?, ?, ?, ?, CAST(? AS BIGINT[]), ?)")) {
            insert.setLong(1, sessionFileId);
            insert.setInt(2, sessionLineIndex);
            insert.setTimestamp(3, Timestamp.valueOf(timestamp));
            insert.setString(4, message);
            insert.setString(5, PackedFormatting.toSqlLiteral(formatting));
            if (currentPlace == null) {
                insert.setNull(6, Types.VARCHAR);
            } else {
                insert.setString(6, currentPlace);
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
