package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.FileSession;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.PlaceMark;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;
import org.duckdb.DuckDBConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimates playtime from imported logs. Each log file contributes the time from its first line to its last,
 * under the version stored on that file. Overlapping files are clipped in {@link PlaytimeSessions} so the same
 * moment is not added twice.
 * <p>
 * Server and world changes come from the place stamped on chat lines. A connect line is not its own event, so
 * time on a new server counts toward the previous place until the first chat line there. Recording those changes
 * at import needs a schema change and is left as a follow-up.
 */
public final class PlaytimeQueries {
    private final DuckDBConnection connection;

    public PlaytimeQueries(DuckDBConnection connection) {
        this.connection = connection;
    }

    public List<PlaytimeSpan> load() {
        try {
            return PlaytimeSessions.build(files(), places());
        } catch (SQLException e) {
            throw new LogDataException("could not read playtime", e);
        }
    }

    private List<FileSession> files() throws SQLException {
        List<FileSession> files = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT id, minecraft_version, start_time, end_time, minecraft_user
                 FROM log_file
                 WHERE end_time > start_time
                 ORDER BY id""")) {
            while (result.next()) {
                files.add(new FileSession(
                    result.getLong(1),
                    result.getString(2),
                    result.getTimestamp(3).toLocalDateTime(),
                    result.getTimestamp(4).toLocalDateTime(),
                    result.getString(5)));
            }
        }
        return files;
    }

    /**
     * One row per place or player change inside a file, including a change to no place. The first tagged message
     * is a change from nothing, so time before that message stays unassigned.
     */
    private List<PlaceMark> places() throws SQLException {
        List<PlaceMark> marks = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT file_id, entry_time, line_index, server_or_world, minecraft_user
                 FROM (
                     SELECT file_id, entry_time, line_index, server_or_world, minecraft_user,
                            lag(server_or_world) OVER per_file AS prev_server,
                            lag(minecraft_user) OVER per_file AS prev_user
                     FROM chat_entry
                     WINDOW per_file AS (PARTITION BY file_id ORDER BY entry_time, line_index)
                 ) marked
                 WHERE server_or_world IS DISTINCT FROM prev_server
                    OR minecraft_user IS DISTINCT FROM prev_user
                 ORDER BY file_id, entry_time, line_index""")) {
            while (result.next()) {
                marks.add(new PlaceMark(
                    result.getLong(1),
                    result.getTimestamp(2).toLocalDateTime(),
                    result.getInt(3),
                    result.getString(4),
                    result.getString(5)));
            }
        }
        return marks;
    }
}
