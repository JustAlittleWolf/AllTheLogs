package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.api.ChatQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a {@link ChatQuery} into SQL.
 * <p>
 * Queries select entry columns; log metadata is loaded separately and joined in Java by
 * {@code file_id}. Context lines expand each match into concrete {@code (file_id, line_index)} keys and hash-join
 * them back, rather than using a range predicate that DuckDB cannot hash.
 * <p>
 * A timestamp {@link ChatQuery#offset()} filters which rows count as matches, in the sort direction. Context expansion
 * does not apply that bound, so surrounding lines may fall on the other side of the cursor.
 * {@link ChatQuery#startingAt} and {@link ChatQuery#upUntil} still clip both matches and context. When context is
 * requested, {@link ChatQuery#limit()} applies to the match set
 * before expansion, so a page of N matches still includes their surrounding lines.
 * {@link ChatQuery#withVersion} keeps matches whose log has that Minecraft version; context stays in the same log.
 */
public final class QueryBuilder {
    private static final String SELECT_COLUMNS = "SELECT e.file_id, e.entry_time, e.line_index, e.message, to_json(e.formatting)";

    private final String sql;
    private final List<Object> parameters;

    private QueryBuilder(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public static QueryBuilder build(ChatQuery query) {
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        addMatchConditions(query, true, conditions, parameters);

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String order = orderBy(query, "e.entry_time", "e.file_id", "e.line_index");
        boolean expandContext = query.contextLines() > 0 && query.hasTextFilter();
        String limit = query.limit() < 0 || expandContext ? "" : " LIMIT " + query.limit() + offsetSql(query);

        String sql;
        if (!expandContext) {
            sql = SELECT_COLUMNS + " FROM chat_entry e"
                + where
                + " " + order + limit;
        } else {
            int context = query.contextLines();
            List<String> contextFilters = new ArrayList<>();
            // The time window must also constrain the context rows, otherwise a match at the edge of the range would
            // pull in neighbours from outside it. The timestamp offset does not: it is a pagination cursor, and
            // context is allowed to extend beyond it.
            if (query.startingAt() != null) {
                contextFilters.add("e.entry_time >= ?");
                parameters.add(Timestamp.valueOf(query.startingAt()));
            }
            if (query.upUntil() != null) {
                contextFilters.add("e.entry_time < ?");
                parameters.add(Timestamp.valueOf(query.upUntil()));
            }
            String contextWhere = contextFilters.isEmpty() ? "" : " WHERE " + String.join(" AND ", contextFilters);
            String matchOrder = orderBy(query, "entry_time", "file_id", "line_index");
            String matchLimit = query.limit() < 0 ? "" : " " + matchOrder + " LIMIT " + query.limit() + offsetSql(query);
            sql = "WITH matches AS (SELECT file_id, line_index FROM chat_entry" + where + matchLimit + "), "
                + "wanted AS (SELECT DISTINCT m.file_id, m.line_index + o.offset AS line_index FROM matches m, "
                + "(SELECT unnest(range(-" + context + ", " + context + " + 1)) AS offset) o) "
                + SELECT_COLUMNS
                + " FROM wanted w INNER JOIN chat_entry e ON e.file_id = w.file_id AND e.line_index = w.line_index"
                + contextWhere
                + " " + order;
        }
        return new QueryBuilder(sql, parameters);
    }

    /**
     * Occupied match days with first/last timestamps and counts, oldest first. One aggregation over matching
     * timestamps; cheaper than selecting every matching row. Ignores context, limit, and offset.
     */
    public static QueryBuilder summary(ChatQuery query) {
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        addMatchConditions(query, false, conditions, parameters);
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String sql = "SELECT CAST(e.entry_time AS DATE), MIN(e.entry_time), MAX(e.entry_time), COUNT(*)"
            + " FROM chat_entry e" + where + " GROUP BY 1 ORDER BY 1";
        return new QueryBuilder(sql, parameters);
    }

    private static String offsetSql(ChatQuery query) {
        return query.skip() > 0 ? " OFFSET " + query.skip() : "";
    }

    /**
     * Number of matching entries for {@code query}. Honours offset and limit; ignores context lines, which are
     * not matches. Callers that want the unpaged total should pass a query with no offset and {@code limit < 0}.
     */
    public static QueryBuilder matches(ChatQuery query) {
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        addMatchConditions(query, true, conditions, parameters);
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String from = " FROM chat_entry e" + where;
        String sql;
        if (query.limit() < 0 && query.skip() <= 0) {
            sql = "SELECT COUNT(*)" + from;
        } else {
            String order = orderBy(query, "e.entry_time", "e.file_id", "e.line_index");
            String cap = query.limit() < 0 ? "" : " LIMIT " + query.limit();
            sql = "SELECT COUNT(*) FROM (SELECT 1" + from + " " + order + cap + offsetSql(query) + ")";
        }
        return new QueryBuilder(sql, parameters);
    }

    private static void addMatchConditions(ChatQuery query, boolean includeOffset, List<String> conditions,
                                           List<Object> parameters) {
        if (query.startingAt() != null) {
            conditions.add("entry_time >= ?");
            parameters.add(Timestamp.valueOf(query.startingAt()));
        }
        if (query.upUntil() != null) {
            conditions.add("entry_time < ?");
            parameters.add(Timestamp.valueOf(query.upUntil()));
        }
        if (includeOffset) {
            addOffsetCondition(query, conditions, parameters);
        }
        if (query.version() != null) {
            conditions.add("file_id IN (SELECT id FROM log_file WHERE minecraft_version = ?)");
            parameters.add(query.version());
        }
        if (query.substring() != null) {
            if (query.caseSensitive()) {
                conditions.add("contains(message, ?)");
                parameters.add(query.substring());
            } else {
                conditions.add("contains(lower(message), ?)");
                parameters.add(query.substring().toLowerCase(Locale.ROOT));
            }
        }
        if (query.regex() != null) {
            conditions.add("regexp_matches(message, ?)");
            parameters.add(query.regex());
        }
    }

    private static void addOffsetCondition(ChatQuery query, List<String> conditions, List<Object> parameters) {
        if (query.offset() == null) return;
        // Exclusive in the sort direction so (limit, offset=lastTimestamp) is the next page without repeating the
        // last match. Context is added later without this predicate.
        if (query.sort() == ChatQuery.Sort.DESCENDING) {
            conditions.add("entry_time < ?");
        } else {
            conditions.add("entry_time > ?");
        }
        parameters.add(Timestamp.valueOf(query.offset()));
    }

    private static String orderBy(ChatQuery query, String time, String file, String line) {
        if (query.sort() == ChatQuery.Sort.DESCENDING) {
            return "ORDER BY " + time + " DESC, " + file + " DESC, " + line + " DESC";
        }
        return "ORDER BY " + time + " ASC, " + file + " ASC, " + line + " ASC";
    }

    public String sql() {
        return sql;
    }

    public void bind(PreparedStatement statement) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object parameter = parameters.get(i);
            if (parameter instanceof Timestamp timestamp) {
                statement.setTimestamp(i + 1, timestamp);
            } else if (parameter instanceof Integer integer) {
                statement.setInt(i + 1, integer);
            } else {
                statement.setString(i + 1, (String) parameter);
            }
        }
    }
}
