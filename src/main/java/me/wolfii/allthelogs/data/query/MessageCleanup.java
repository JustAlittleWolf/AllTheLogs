package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import me.wolfii.allthelogs.data.parse.TextRemoval;
import me.wolfii.allthelogs.data.store.FileAggregates;
import me.wolfii.allthelogs.data.store.StoreCancellation;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deletes stored chat entries, or cuts text out of them, for every entry a {@link ChatQuery} matches.
 * Each call is one transaction, and per-file entry counts are refreshed before it commits. The caller
 * compacts the store afterwards, because DuckDB keeps the old bytes in the file.
 */
public final class MessageCleanup {
    private final DuckDBConnection connection;

    public MessageCleanup(DuckDBConnection connection) {
        this.connection = connection;
    }

    /**
     * @param changed entries whose text was shortened
     * @param deleted entries removed, including those left with only whitespace
     */
    public record Outcome(long changed, long deleted) {
    }

    public Outcome delete(ChatQuery query) {
        ChatQueries.rejectUnsupportedRegex(query);
        QueryBuilder builder = QueryBuilder.delete(query);
        return inTransaction(query, statement -> {
            long deleted;
            try (PreparedStatement delete = connection.prepareStatement(builder.sql())) {
                builder.bind(delete);
                deleted = delete.executeLargeUpdate();
            }
            FileAggregates.refreshEntryCounts(statement);
            return new Outcome(0, deleted);
        });
    }

    /**
     * Removes every match of {@code part} from the matching entries. Entries left blank are deleted.
     */
    public Outcome removeText(ChatQuery query, Pattern part) {
        ChatQueries.rejectUnsupportedRegex(query);
        QueryBuilder builder = QueryBuilder.rewriteCandidates(query);
        return inTransaction(query, statement -> {
            List<Long> changedIds = new ArrayList<>();
            List<FormattingCodes.Parsed> changed = new ArrayList<>();
            List<Long> emptied = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(builder.sql())) {
                builder.bind(select);
                try (ResultSet result = select.executeQuery()) {
                    while (result.next()) {
                        if (((changedIds.size() + emptied.size()) & 1023) == 0) StoreCancellation.throwIfStopped();
                        FormattingCodes.Parsed stored = new FormattingCodes.Parsed(result.getString(2),
                            PackedFormatting.fromSqlLiteral(result.getString(3)));
                        FormattingCodes.Parsed kept = TextRemoval.remove(stored, part);
                        if (kept == stored) continue;
                        if (kept.text().isBlank()) {
                            emptied.add(result.getLong(1));
                        } else {
                            changedIds.add(result.getLong(1));
                            changed.add(kept);
                        }
                    }
                }
            }
            if (!changedIds.isEmpty()) rewrite(statement, changedIds, changed);
            if (!emptied.isEmpty()) deleteRows(statement, emptied);
            if (!emptied.isEmpty()) FileAggregates.refreshEntryCounts(statement);
            return new Outcome(changedIds.size(), emptied.size());
        });
    }

    /**
     * One {@code UPDATE} from a temporary table. Per-row updates rewrite DuckDB row groups one line at a time.
     */
    private void rewrite(Statement statement, List<Long> rowIds, List<FormattingCodes.Parsed> lines) throws SQLException {
        statement.execute("CREATE TEMP TABLE chat_entry_cleanup (rid BIGINT, message VARCHAR, formatting BIGINT[])");
        try {
            try (DuckDBAppender appender = connection.createAppender("temp", DuckDBConnection.DEFAULT_SCHEMA,
                "chat_entry_cleanup")) {
                for (int i = 0; i < rowIds.size(); i++) {
                    appender.beginRow();
                    appender.append(rowIds.get(i));
                    appender.append(lines.get(i).text());
                    long[] packed = lines.get(i).formatting();
                    if (packed == null) appender.appendNull();
                    else appender.append(packed);
                    appender.endRow();
                }
            }
            statement.execute("""
                UPDATE chat_entry
                SET message = r.message,
                    formatting = r.formatting
                FROM chat_entry_cleanup r
                WHERE chat_entry.rowid = r.rid""");
        } finally {
            statement.execute("DROP TABLE IF EXISTS chat_entry_cleanup");
        }
    }

    private void deleteRows(Statement statement, List<Long> rowIds) throws SQLException {
        statement.execute("CREATE TEMP TABLE chat_entry_cleanup_drop (rid BIGINT)");
        try {
            try (DuckDBAppender appender = connection.createAppender("temp", DuckDBConnection.DEFAULT_SCHEMA,
                "chat_entry_cleanup_drop")) {
                for (long rowId : rowIds) {
                    appender.beginRow();
                    appender.append(rowId);
                    appender.endRow();
                }
            }
            statement.execute("DELETE FROM chat_entry WHERE rowid IN (SELECT rid FROM chat_entry_cleanup_drop)");
        } finally {
            statement.execute("DROP TABLE IF EXISTS chat_entry_cleanup_drop");
        }
    }

    private Outcome inTransaction(ChatQuery query, Work work) {
        boolean open = false;
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN TRANSACTION");
            open = true;
            Outcome outcome = work.run(statement);
            statement.execute("COMMIT");
            open = false;
            return outcome;
        } catch (SQLException | RuntimeException e) {
            if (open) rollback();
            if (e instanceof LogDataException data) throw data;
            throw ChatQueries.queryFailed("could not clean up messages", query, e);
        }
    }

    private void rollback() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException ignored) {
            // The original failure is the one worth reporting.
        }
    }

    @FunctionalInterface
    private interface Work {
        Outcome run(Statement statement) throws SQLException;
    }
}
