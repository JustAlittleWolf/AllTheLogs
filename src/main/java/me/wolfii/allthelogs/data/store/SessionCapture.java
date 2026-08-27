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
import java.util.Objects;

/**
 * Captures chat lines from a running Minecraft client into a {@link LogSource.Session} log.
 */
public final class SessionCapture {
    private static final String SESSION_SOURCE_PATH = "<session>";

    private final DuckDBConnection connection;
    private long sessionFileId = -1;
    private int sessionLineIndex;

    public SessionCapture(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * Starts a capture session at {@code startedAt} (whole seconds) and returns the created log, which carries a
     * unique {@link LogSource.Session#id()}.
     *
     * @throws LogDataException if the session cannot be written
     */
    public ChatLog start(String minecraftVersion, LocalDateTime startedAt) {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(startedAt, "startedAt");
        LocalDateTime start = startedAt.withNano(0);
        String sessionId = SessionMarker.newId();
        try {
            long fileId = nextFileId();
            LocalDate date = start.toLocalDate();
            String entryPath = SessionMarker.entryPath(sessionId);
            Timestamp timestamp = Timestamp.valueOf(start);
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO log_file (id, file_name, source_kind, source_path, entry_path, log_date,
                                      minecraft_version, start_time, end_time, entry_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)""")) {
                insert.setLong(1, fileId);
                insert.setString(2, "");
                insert.setString(3, SourceKind.SESSION.name());
                insert.setString(4, SESSION_SOURCE_PATH);
                insert.setString(5, entryPath);
                insert.setDate(6, Date.valueOf(date));
                insert.setString(7, minecraftVersion);
                insert.setTimestamp(8, timestamp);
                insert.setTimestamp(9, timestamp);
                insert.execute();
            }
            sessionFileId = fileId;
            sessionLineIndex = 0;
            return new ChatLog(new LogSource.Session(sessionId), date, minecraftVersion, start, start);
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

    public boolean importMessage(String message, int[] formatting, LocalDateTime timestamp) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
        requireActiveSession();
        LocalDateTime stamp = timestamp.withNano(0);
        String text;
        int[] packed;
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
        LocalDateTime stamp = timestamp.withNano(0);
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

    private void requireActiveSession() {
        if (sessionFileId < 0) {
            throw new LogDataException("no client session is active; call startSession first");
        }
    }

    private boolean writeEntry(String message, int[] formatting, LocalDateTime timestamp) throws SQLException {
        try (PreparedStatement duplicate = connection.prepareStatement(
            "SELECT 1 FROM chat_entry WHERE entry_time = ? AND message = ? LIMIT 1")) {
            duplicate.setTimestamp(1, Timestamp.valueOf(timestamp));
            duplicate.setString(2, message);
            try (ResultSet result = duplicate.executeQuery()) {
                if (result.next()) return false;
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO chat_entry (file_id, line_index, entry_time, message, formatting) VALUES (?, ?, ?, ?, ?)")) {
            insert.setLong(1, sessionFileId);
            insert.setInt(2, sessionLineIndex);
            insert.setTimestamp(3, Timestamp.valueOf(timestamp));
            insert.setString(4, message);
            insert.setString(5, PackedFormatting.toSqlLiteral(formatting));
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
