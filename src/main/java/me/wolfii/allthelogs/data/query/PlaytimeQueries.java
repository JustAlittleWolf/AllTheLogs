package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.extract.MinecraftVersionExtractor;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.FileSession;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.PlaceMark;
import me.wolfii.allthelogs.data.playtime.PlaytimeSessions.VersionMark;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;
import org.duckdb.DuckDBConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimates playtime from imported logs. Each log file contributes the time from its first line to its last.
 * Server and world changes come from the place stamped on chat lines. A chat line that itself names a Minecraft
 * version starts a new version from that moment; the version stored on the file covers everything before that.
 */
public final class PlaytimeQueries {
    private final DuckDBConnection connection;

    public PlaytimeQueries(DuckDBConnection connection) {
        this.connection = connection;
    }

    public List<PlaytimeSpan> load() {
        try {
            return PlaytimeSessions.build(files(), places(), versions());
        } catch (SQLException e) {
            throw new LogDataException("could not read playtime", e);
        }
    }

    private List<FileSession> files() throws SQLException {
        List<FileSession> files = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT id, minecraft_version, start_time, end_time
                 FROM log_file
                 WHERE end_time > start_time
                 ORDER BY id""")) {
            while (result.next()) {
                files.add(new FileSession(
                    result.getLong(1),
                    result.getString(2),
                    result.getTimestamp(3).toLocalDateTime(),
                    result.getTimestamp(4).toLocalDateTime()));
            }
        }
        return files;
    }

    /**
     * One row per place change inside a file, including a change to no place. The first tagged message is a
     * change from nothing, so time before that message stays unassigned.
     */
    private List<PlaceMark> places() throws SQLException {
        List<PlaceMark> marks = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT file_id, entry_time, line_index, server_or_world
                 FROM (
                     SELECT file_id, entry_time, line_index, server_or_world,
                            lag(server_or_world) OVER (
                                PARTITION BY file_id
                                ORDER BY entry_time, line_index
                            ) AS prev_server
                     FROM chat_entry
                 ) marked
                 WHERE server_or_world IS DISTINCT FROM prev_server
                 ORDER BY file_id, entry_time, line_index""")) {
            while (result.next()) {
                marks.add(new PlaceMark(
                    result.getLong(1),
                    result.getTimestamp(2).toLocalDateTime(),
                    result.getInt(3),
                    result.getString(4)));
            }
        }
        return marks;
    }

    private List<VersionMark> versions() throws SQLException {
        List<VersionMark> marks = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT file_id, entry_time, line_index, message
                 FROM chat_entry
                 WHERE contains(lower(message), 'minecraft')
                    OR contains(lower(message), 'optifine')
                    OR contains(message, '--version')
                 ORDER BY file_id, entry_time, line_index""")) {
            while (result.next()) {
                String version = versionOf(result.getString(4));
                if (version == null) continue;
                marks.add(new VersionMark(
                    result.getLong(1),
                    result.getTimestamp(2).toLocalDateTime(),
                    result.getInt(3),
                    version));
            }
        }
        return marks;
    }

    /**
     * Version named by one stored message, or null when the line is not a version source.
     */
    static String versionOf(String message) {
        if (message == null || message.isBlank()) return null;
        MinecraftVersionExtractor extractor = new MinecraftVersionExtractor();
        extractor.accept(message);
        return extractor.version();
    }
}
