package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogStoreMetadata;
import me.wolfii.allthelogs.data.store.StoredSources;
import org.duckdb.DuckDBConnection;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads chat-log rows and store-wide summaries. Match queries live in {@link ChatQueries}.
 */
final class CatalogQueries {
    private final DuckDBConnection connection;

    CatalogQueries(DuckDBConnection connection) {
        this.connection = connection;
    }

    static ChatLog readChatLog(ResultSet result, int offset) throws SQLException {
        return new ChatLog(
            StoredSources.fromStored(
                result.getString(offset + 1),
                result.getString(offset + 2),
                result.getString(offset + 3)),
            result.getDate(offset + 4).toLocalDate(),
            result.getString(offset + 5),
            result.getTimestamp(offset + 6).toLocalDateTime(),
            result.getTimestamp(offset + 7).toLocalDateTime(),
            result.getString(offset + 8));
    }

    List<ChatLog> chatLogs() {
        List<ChatLog> logs = new ArrayList<>();
        String sql = """
            SELECT file_name, source_kind, source_path, entry_path, log_date, minecraft_version,
                   start_time, end_time, minecraft_user
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

    LogStoreMetadata metadata(long databaseSizeBytes) {
        try {
            List<String> versions = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                     SELECT minecraft_version
                     FROM log_file
                     GROUP BY minecraft_version
                     ORDER BY MIN(log_date), minecraft_version""")) {
                while (result.next()) {
                    versions.add(result.getString(1));
                }
            }
            long chatLogCount;
            LocalDate firstLogDate;
            LocalDate lastLogDate;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*), MIN(log_date), MAX(log_date) FROM log_file")) {
                result.next();
                chatLogCount = result.getLong(1);
                Date first = result.getDate(2);
                Date last = result.getDate(3);
                firstLogDate = first == null ? null : first.toLocalDate();
                lastLogDate = last == null ? null : last.toLocalDate();
            }
            long chatEntryCount;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM chat_entry")) {
                result.next();
                chatEntryCount = result.getLong(1);
            }
            return new LogStoreMetadata(versions, firstLogDate, lastLogDate, chatLogCount, chatEntryCount,
                databaseSizeBytes);
        } catch (SQLException e) {
            throw new LogDataException("could not read store metadata", e);
        }
    }

    long reportedDatabaseSize() {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT COALESCE(SUM(memory_usage_bytes), 0) FROM duckdb_memory()")) {
            if (!result.next()) {
                throw new LogDataException("could not read database size");
            }
            return result.getLong(1);
        } catch (SQLException e) {
            throw new LogDataException("could not read database size", e);
        }
    }
}
