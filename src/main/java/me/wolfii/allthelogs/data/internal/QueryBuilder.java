package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.ChatQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Turns a [ChatQuery] into SQL.
///
/// Queries select only the four entry columns. Log metadata is loaded separately and joined in Java by `file_id`,
/// so DuckDB does not repeat file strings on every row of a multi-million result. Context lines are resolved inside
/// the database: matching rows are expanded into the concrete `(file_id, line_index)` keys they want, then hash-joined
/// back to `chat_entry`. DISTINCT collapses overlapping windows, so every row is returned at most once.
public final class QueryBuilder {
    private static final String SELECT_COLUMNS = "SELECT e.file_id, e.entry_time, e.line_index, e.message";

    private final String sql;
    private final List<Object> parameters;

    private QueryBuilder(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
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
                // Lower the needle in Java so DuckDB only lowercases the column, not the same constant per row.
                conditions.add("contains(lower(message), ?)");
                parameters.add(query.substring().toLowerCase(Locale.ROOT));
            }
        }
        if (query.regex() != null) {
            conditions.add("regexp_matches(message, ?)");
            parameters.add(query.regex());
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String order = "ORDER BY e.entry_time " + (query.descending() ? "DESC" : "ASC") + ", e.file_id, e.line_index";
        String limit = query.limit() < 0 ? "" : " LIMIT " + query.limit();

        String sql;
        if (query.contextLines() == 0 || !query.hasTextFilter()) {
            sql = SELECT_COLUMNS + " FROM chat_entry e"
                + where
                + " " + order + limit;
        } else {
            // Expanding each match into the concrete line indices it wants, rather than asking for every row whose
            // index falls in a BETWEEN window, turns the lookup into an equality join. DuckDB can hash that, while a
            // range predicate forces it to compare each entry against the match set, which gets dramatically slower
            // as the number of matches grows. DISTINCT collapses the overlapping windows of neighbouring matches, so
            // the join still returns every row at most once.
            int context = query.contextLines();
            List<String> contextFilters = new ArrayList<>();
            if (query.from() != null) {
                contextFilters.add("e.entry_time >= ?");
                parameters.add(Timestamp.valueOf(query.from()));
            }
            if (query.to() != null) {
                contextFilters.add("e.entry_time < ?");
                parameters.add(Timestamp.valueOf(query.to()));
            }
            String contextWhere = contextFilters.isEmpty() ? "" : " WHERE " + String.join(" AND ", contextFilters);
            sql = "WITH matches AS (SELECT file_id, line_index FROM chat_entry" + where + "), "
                + "wanted AS (SELECT DISTINCT m.file_id, m.line_index + o.offset AS line_index FROM matches m, "
                + "(SELECT unnest(range(-" + context + ", " + context + " + 1)) AS offset) o) "
                + SELECT_COLUMNS
                + " FROM wanted w INNER JOIN chat_entry e ON e.file_id = w.file_id AND e.line_index = w.line_index"
                + contextWhere
                + " " + order + limit;
        }
        return new QueryBuilder(sql, parameters);
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
}
