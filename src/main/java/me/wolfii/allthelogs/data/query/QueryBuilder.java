package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.store.StoredSources;

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
 * {@code file_id}. Context lines take the next {@code n} stored chat lines before and after each match
 * in that file that still pass the date window and server filter, skipping neighbours that do not.
 * <p>
 * A timestamp {@link ChatQuery#offset()} filters which rows count as matches, in the sort direction. A row
 * cursor ({@link ChatQuery#offsetSource()}) is exclusive on {@code (entry_time, file_id, line_index)} so
 * paging after a line still returns other matches that share that second. Context expansion
 * does not apply that bound, so surrounding lines may fall on the other side of the cursor.
 * {@link ChatQuery#startingAt} and {@link ChatQuery#upUntil} still clip both matches and context. When context is
 * requested, {@link ChatQuery#limit()} applies to the match set
 * before expansion, so a page of N matches still includes their surrounding lines.
 * {@link ChatQuery#withVersion} keeps matches whose log has that Minecraft version; context stays in the same log.
 * {@link ChatQuery#withServerOrWorld} keeps matches whose server or world contains that text
 * (case insensitive) and also clips context lines, because one log can visit several servers.
 */
public final class QueryBuilder {
    private static final String SELECT_COLUMNS = "SELECT e.file_id, e.entry_time, e.line_index, e.message, to_json(e.formatting), e.minecraft_user, e.server_or_world";

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
            String matchOrder = orderBy(query, "entry_time", "file_id", "line_index");
            String matchLimit = query.limit() < 0 ? "" : " " + matchOrder + " LIMIT " + query.limit() + offsetSql(query);
            String beforeSql = lateralContextSql(query, parameters, "<", "DESC", context);
            String afterSql = lateralContextSql(query, parameters, ">", "ASC", context);
            sql = "WITH matches AS (SELECT file_id, line_index FROM chat_entry" + where + matchLimit + "), "
                + "wanted AS ("
                + "SELECT file_id, line_index FROM matches "
                + "UNION SELECT lat.file_id, lat.line_index FROM matches m, LATERAL (" + beforeSql + ") lat "
                + "UNION SELECT lat.file_id, lat.line_index FROM matches m, LATERAL (" + afterSql + ") lat"
                + ") "
                + SELECT_COLUMNS
                + " FROM wanted w INNER JOIN chat_entry e ON e.file_id = w.file_id AND e.line_index = w.line_index"
                + " " + order;
        }
        return new QueryBuilder(sql, parameters);
    }

    /**
     * Next {@code context} chat lines on one side of a match that still pass the date window and server
     * filter. The timestamp offset does not apply: it is a pagination cursor, and context may extend
     * beyond it.
     */
    private static String lateralContextSql(ChatQuery query, List<Object> parameters, String lineCmp,
                                            String lineOrder, int context) {
        List<String> filters = new ArrayList<>();
        filters.add("e.file_id = m.file_id");
        filters.add("e.line_index " + lineCmp + " m.line_index");
        if (query.startingAt() != null) {
            filters.add("e.entry_time >= ?");
            parameters.add(Timestamp.valueOf(query.startingAt()));
        }
        if (query.upUntil() != null) {
            filters.add("e.entry_time < ?");
            parameters.add(Timestamp.valueOf(query.upUntil()));
        }
        if (query.serverOrWorld() != null) {
            filters.add("contains(lower(e.server_or_world), ?)");
            parameters.add(query.serverOrWorld().toLowerCase(Locale.ROOT));
        }
        return "SELECT e.file_id, e.line_index FROM chat_entry e WHERE "
            + String.join(" AND ", filters)
            + " ORDER BY e.line_index " + lineOrder + " LIMIT " + context;
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
        if (query.serverOrWorld() != null) {
            conditions.add("contains(lower(server_or_world), ?)");
            parameters.add(query.serverOrWorld().toLowerCase(Locale.ROOT));
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
        if (query.offsetSource() != null) {
            addRowCursorCondition(query, conditions, parameters);
            return;
        }
        // Exclusive in the sort direction so (limit, offset=lastTimestamp) is the next page without repeating the
        // last match. Context is added later without this predicate. The bound is a DuckDB TIMESTAMP (microseconds);
        // callers that need the cursor second included must nudge by 1µs, not 1ns.
        if (query.sort() == ChatQuery.Sort.DESCENDING) {
            conditions.add("entry_time < ?");
        } else {
            conditions.add("entry_time > ?");
        }
        parameters.add(Timestamp.valueOf(query.offset()));
    }

    /**
     * Exclusive on {@code (entry_time, file_id, line_index)} so paging after a line keeps the rest of that
     * second. Timestamp-only {@link ChatQuery#offset()} cannot do that.
     */
    private static void addRowCursorCondition(ChatQuery query, List<String> conditions, List<Object> parameters) {
        LogSource source = dataSource(query.offsetSource());
        String cmp = query.sort() == ChatQuery.Sort.DESCENDING ? "<" : ">";
        String fileId = "(SELECT id FROM log_file WHERE source_path = ? AND entry_path = ?)";
        conditions.add("(entry_time " + cmp + " ? OR (entry_time = ? AND file_id = " + fileId
            + " AND line_index " + cmp + " ?) OR (entry_time = ? AND file_id " + cmp + " " + fileId + "))");
        Timestamp time = Timestamp.valueOf(query.offset());
        String sourcePath = StoredSources.sourcePath(source);
        String entryPath = StoredSources.entryPath(source);
        parameters.add(time);
        parameters.add(time);
        parameters.add(sourcePath);
        parameters.add(entryPath);
        parameters.add(query.offsetLine());
        parameters.add(time);
        parameters.add(sourcePath);
        parameters.add(entryPath);
    }

    private static LogSource dataSource(me.wolfii.allthelogs.api.LogSource source) {
        if (source instanceof LogSource dataSource) return dataSource;
        throw new IllegalArgumentException("offsetSource");
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
