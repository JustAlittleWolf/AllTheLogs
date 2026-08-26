package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.ChatQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/// Turns a [ChatQuery] into SQL.
///
/// Context lines are resolved inside the database rather than by post processing in Java: the matching rows are found
/// once, then joined back against the entry table on `(file_id, line_index)` within the requested window. Because that
/// join produces every context row at most once regardless of how many matches it neighbours, overlapping windows are
/// deduplicated for free.
public final class QueryBuilder {
    private static final String SELECT_COLUMNS = """
            SELECT f.file_name, f.source_kind, f.source_path, f.entry_path, f.log_date, f.date_source,
                   f.minecraft_version, f.last_modified, f.first_entry_time, f.last_entry_time, f.entry_count,
                   e.entry_time, e.line_index, e.message
            """;

    private final String sql;
    private final List<Object> parameters;

    private QueryBuilder(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public String sql() {
        return sql;
    }

    public void bind(PreparedStatement statement) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object parameter = parameters.get(i);
            if (parameter instanceof Timestamp timestamp) {
                statement.setTimestamp(i + 1, timestamp);
            } else {
                statement.setString(i + 1, (String) parameter);
            }
        }
    }

    public static QueryBuilder build(ChatQuery query) {
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (query.from() != null) {
            conditions.add("entry_time >= ?");
            parameters.add(Timestamp.valueOf(query.from()));
        }
        if (query.to() != null) {
            conditions.add("entry_time < ?");
            parameters.add(Timestamp.valueOf(query.to()));
        }
        if (query.substring() != null) {
            if (query.caseSensitive()) {
                conditions.add("contains(message, ?)");
                parameters.add(query.substring());
            } else {
                conditions.add("contains(lower(message), lower(?))");
                parameters.add(query.substring());
            }
        }
        if (query.regex() != null) {
            conditions.add("regexp_matches(message, ?)");
            parameters.add(query.regex());
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String order = "ORDER BY e.entry_time " + (query.descending() ? "DESC" : "ASC") + ", f.entry_path, e.line_index";
        String limit = query.limit() < 0 ? "" : " LIMIT " + query.limit();

        String sql;
        if (query.contextLines() == 0 || !query.hasTextFilter()) {
            sql = SELECT_COLUMNS + " FROM chat_entry e JOIN log_file f ON f.id = e.file_id"
                    + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                    + " " + order + limit;
        } else {
            // The date range must also constrain the context rows, otherwise a match at the edge of the range would
            // pull in neighbours from outside it.
            List<String> contextConditions = new ArrayList<>();
            contextConditions.add("e.file_id = m.file_id");
            contextConditions.add("e.line_index BETWEEN m.line_index - " + query.contextLines()
                    + " AND m.line_index + " + query.contextLines());
            if (query.from() != null) {
                contextConditions.add("e.entry_time >= ?");
                parameters.add(Timestamp.valueOf(query.from()));
            }
            if (query.to() != null) {
                contextConditions.add("e.entry_time < ?");
                parameters.add(Timestamp.valueOf(query.to()));
            }
            sql = "WITH matches AS (SELECT file_id, line_index FROM chat_entry" + where + ") "
                    + SELECT_COLUMNS
                    + " FROM chat_entry e JOIN log_file f ON f.id = e.file_id"
                    + " WHERE EXISTS (SELECT 1 FROM matches m WHERE " + String.join(" AND ", contextConditions) + ") "
                    + order + limit;
        }
        return new QueryBuilder(sql, parameters);
    }

    /// Builds the SQL for counting matching entries, ignoring context lines, ordering and limit.
    public static QueryBuilder buildCount(ChatQuery query) {
        QueryBuilder full = build(query.withContextLines(0).withLimit(-1));
        int fromIndex = full.sql.indexOf(" FROM chat_entry e");
        int orderIndex = full.sql.indexOf(" ORDER BY ");
        String body = full.sql.substring(fromIndex, orderIndex);
        return new QueryBuilder("SELECT count(*)" + body, full.parameters);
    }
}
