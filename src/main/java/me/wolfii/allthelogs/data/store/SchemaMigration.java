package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.MessageCharacters;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records the schema version written by this build and steps an older, existing database forward to it.
 * <p>
 * A database with no version at all (predates versioning) or a version older than
 * {@link #MIGRATIONS}. A version bump that needs no data changes (a purely additive, backward-compatible
 * schema/behavior change) still gets an entry here — registered as {@code (statement) -> false} — so a
 * missing entry always means "forgotten", never "not needed". A step returns {@code true} when it
 * rewrote stored rows and the whole store should be optimized afterwards.
 */
public final class SchemaMigration {
    private static final Migration NO_OP = (_) -> false;

    /**
     * Step {@code v} takes a database from version {@code v} to {@code v + 1}. Applied in a loop by
     * {@link #migrate}, which advances and persists the version number one step at a time so a failure
     * partway through a multi-step migration doesn't have to be redone from the start on retry.
     */
    private static final Map<Integer, Migration> MIGRATIONS = Map.of(
        1, NO_OP,
        2, NO_OP,
        3, SchemaMigration::migrate3To4SeedClusterMarker,
        4, SchemaMigration::migrate4To5PerEntryMetadata,
        5, SchemaMigration::migrate5To6StripPlaceholderCharacters
    );

    @FunctionalInterface
    private interface Migration {
        /**
         * @return {@code true} when this step rewrote stored rows and the whole store should be optimized
         */
        boolean apply(Statement statement) throws SQLException;
    }

    private SchemaMigration() {
    }

    /**
     * Ensures a fresh database is at {@link Schema#CURRENT_VERSION}, or steps an existing one forward to it.
     *
     * @return {@code true} when a step rewrote stored rows and the caller should optimize the whole store
     */
    public static boolean migrate(Statement statement) throws SQLException {
        int version = readVersion(statement);
        if (version == 0) {
            Schema.create(statement);
            setVersion(statement, Schema.CURRENT_VERSION);
            return false;
        }
        if (version > Schema.CURRENT_VERSION) {
            throw new SQLException("database schema version " + version
                + " is newer than this mod supports (" + Schema.CURRENT_VERSION + ")");
        }
        if (version < 0) {
            throw new SQLException("database schema version " + version
                + " is too old to migrate; delete it and import again");
        }
        boolean optimize = false;
        while (version < Schema.CURRENT_VERSION) {
            StoreCancellation.throwIfStopped();
            Migration step = MIGRATIONS.get(version);
            if (step == null) {
                throw new SQLException("no migration registered from schema version " + version
                    + " to " + (version + 1));
            }
            System.out.println("[AllTheLogs] Migrating schema from version " + version + " to " + (version + 1));
            optimize |= step.apply(statement);
            version++;
            setVersion(statement, version);
        }
        return optimize;
    }

    /**
     * 3 → 4: adds {@link Schema#CLUSTER_MARKER_KEY}, which {@link Schema#clusterTail} uses to track
     * live-session catch-up.
     * <p>
     * Unlike {@link Schema#create}'s seed for a brand-new database (which starts the marker at the current
     * file id, since nothing has been written yet), an existing database can already hold a long history of
     * live-captured chat that was never clustered — every session before this feature existed left its own
     * small, unsorted tail behind. Seeding the marker at {@code 0} here means the very next
     * {@link Schema#clusterTail} call (at the next session start) treats the *entire* table as pending and
     * sorts all of it, exactly like a manual {@link Schema#clusterEntries} pass, so historical live-recorded
     * data gets optimized too, not just writes made from this point on. That first catch-up after upgrading
     * costs as much as a full cluster; every one after it is back to the normal, cheap, tail-only cost.
     */
    private static boolean migrate3To4SeedClusterMarker(Statement statement) throws SQLException {
        statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('" + Schema.CLUSTER_MARKER_KEY + "', '0')");
        return false;
    }

    /**
     * 4 → 5: stamps each chat line with the Minecraft user and the server or world in effect at
     * that line. Place is per message because one log can visit several servers
     * (connect → leave → connect). Existing rows inherit {@code log_file.minecraft_user};
     * {@code server_or_world} is null until those logs are imported again.
     */
    private static boolean migrate4To5PerEntryMetadata(Statement statement) throws SQLException {
        statement.execute("ALTER TABLE chat_entry ADD COLUMN IF NOT EXISTS minecraft_user VARCHAR");
        statement.execute("ALTER TABLE chat_entry ADD COLUMN IF NOT EXISTS server_or_world VARCHAR");
        statement.execute("""
            UPDATE chat_entry e
            SET minecraft_user = f.minecraft_user
            FROM log_file f
            WHERE e.file_id = f.id AND e.minecraft_user IS NULL AND f.minecraft_user IS NOT NULL""");
        return false;
    }

    /**
     * 5 → 6: removes U+FFFC and Unicode private-use characters from messages already stored, and
     * rebuilds formatting so colours stay on the characters that remain. New lines are filtered as
     * they are written, so this only rewrites an older database.
     * <p>
     * Matching rows are collected once, appended into a temporary table, and applied with a single
     * {@code UPDATE}. Per-row updates rewrite DuckDB row groups one line at a time.
     *
     * @return {@code true} when a stored line changed. In-place string updates leave the old message
     * bytes in the table, and search scans that column, so the caller rewrites the store.
     */
    private static boolean migrate5To6StripPlaceholderCharacters(Statement statement) throws SQLException {
        List<Long> rowIds = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        List<long[]> formatting = new ArrayList<>();
        String query = """
            SELECT rowid, message, to_json(formatting)
            FROM chat_entry
            WHERE contains(message, chr(%d)) OR regexp_matches(message, '\\p{Co}')
            """.formatted(MessageCharacters.OBJECT_REPLACEMENT_CODE_POINT);
        try (ResultSet result = statement.executeQuery(query)) {
            while (result.next()) {
                if ((rowIds.size() & 1023) == 0) StoreCancellation.throwIfStopped();
                long rowId = result.getLong(1);
                String message = result.getString(2);
                FormattingCodes.Parsed filtered = MessageCharacters.filter(
                    new FormattingCodes.Parsed(message, PackedFormatting.fromSqlLiteral(result.getString(3))), true);
                if (message.equals(filtered.text())) continue;
                rowIds.add(rowId);
                messages.add(filtered.text());
                formatting.add(filtered.formatting());
            }
        }
        if (rowIds.isEmpty()) return false;
        StoreCancellation.throwIfStopped();
        System.out.println("[AllTheLogs] Removing placeholder characters from " + rowIds.size() + " chat lines");
        statement.execute("""
            CREATE TEMP TABLE chat_entry_rewrite (
                rid BIGINT,
                message VARCHAR,
                formatting BIGINT[]
            )""");
        try {
            DuckDBConnection connection = statement.getConnection().unwrap(DuckDBConnection.class);
            // Temporary tables are stored in the temp catalog, not the database file's catalog.
            try (DuckDBAppender appender = connection.createAppender(
                "temp", DuckDBConnection.DEFAULT_SCHEMA, "chat_entry_rewrite")) {
                for (int i = 0; i < rowIds.size(); i++) {
                    appender.beginRow();
                    appender.append(rowIds.get(i));
                    appender.append(messages.get(i));
                    long[] packed = formatting.get(i);
                    if (packed == null) {
                        appender.appendNull();
                    } else {
                        appender.append(packed);
                    }
                    appender.endRow();
                }
            }
            statement.execute("""
                UPDATE chat_entry
                SET message = r.message,
                    formatting = r.formatting
                FROM chat_entry_rewrite r
                WHERE chat_entry.rowid = r.rid""");
        } finally {
            statement.execute("DROP TABLE IF EXISTS chat_entry_rewrite");
        }
        return true;
    }

    static int readVersion(Statement statement) throws SQLException {
        if (!tableExists(statement, Schema.META_TABLE)) {
            return 0;
        }
        try (ResultSet result = statement.executeQuery(
            "SELECT v FROM " + Schema.META_TABLE + " WHERE k = '" + Schema.VERSION_KEY + "'")) {
            if (!result.next()) {
                return 0;
            }
            String value = result.getString(1);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new SQLException("invalid schema version: " + value, e);
            }
        }
    }

    private static boolean tableExists(Statement statement, String tableName) throws SQLException {
        try (ResultSet result = statement.executeQuery("""
        SELECT count(*)
        FROM information_schema.tables
        WHERE table_schema = 'main' AND table_name = '""" + tableName + "'")) {
            result.next();
            return result.getLong(1) > 0;
        }
    }

    static void setVersion(Statement statement, int version) throws SQLException {
        statement.execute("DELETE FROM " + Schema.META_TABLE + " WHERE k = '" + Schema.VERSION_KEY + "'");
        statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('" + Schema.VERSION_KEY + "', '" + version + "')");
    }
}
