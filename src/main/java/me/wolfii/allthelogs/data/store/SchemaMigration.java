package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tracks the database schema version and upgrades older files on open.
 * <p>
 * Version history:
 * <ul>
 *   <li>1 — initial {@link Schema} layout ({@code log_file}, {@code chat_entry}, location index)</li>
 * </ul>
 * <p>
 * To bump the schema, increment {@link #CURRENT_VERSION}, add a step in {@link #migrateFrom(Statement, int)},
 * and document the change in this class.
 */
public final class SchemaMigration {
    static final String META_TABLE = "allthelogs_meta";
    static final String VERSION_KEY = "schema_version";

    /** Schema version written by this release. */
    public static final int CURRENT_VERSION = 1;

    private SchemaMigration() {
    }

    /**
     * Ensures the database is at {@link #CURRENT_VERSION}, creating or upgrading it as needed.
     */
    public static void migrate(Statement statement) throws SQLException {
        createMetaTable(statement);
        int version = readVersion(statement);
        if (version == 0) {
            if (hasDataTables(statement)) {
                setVersion(statement, CURRENT_VERSION);
                return;
            }
            Schema.create(statement);
            setVersion(statement, CURRENT_VERSION);
            return;
        }
        if (version > CURRENT_VERSION) {
            throw new SQLException("database schema version " + version
                + " is newer than this mod supports (" + CURRENT_VERSION + ")");
        }
        if (version < CURRENT_VERSION) {
            upgrade(statement, version, CURRENT_VERSION);
        }
    }

    private static void createMetaTable(Statement statement) throws SQLException {
        statement.execute("""
            CREATE TABLE IF NOT EXISTS allthelogs_meta (
                k VARCHAR PRIMARY KEY,
                v VARCHAR NOT NULL
            )""");
    }

    private static boolean hasDataTables(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("""
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'main' AND table_name = 'log_file'
            """)) {
            result.next();
            return result.getLong(1) > 0;
        }
    }

    static int readVersion(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(
            "SELECT v FROM " + META_TABLE + " WHERE k = '" + VERSION_KEY + "'")) {
            if (!result.next()) {
                return 0;
            }
            return Integer.parseInt(result.getString(1));
        }
    }

    private static void setVersion(Statement statement, int version) throws SQLException {
        statement.execute("DELETE FROM " + META_TABLE + " WHERE k = '" + VERSION_KEY + "'");
        statement.execute("INSERT INTO " + META_TABLE + " VALUES ('" + VERSION_KEY + "', '" + version + "')");
    }

    private static void upgrade(Statement statement, int from, int to) throws SQLException {
        for (int version = from; version < to; version++) {
            migrateFrom(statement, version);
            setVersion(statement, version + 1);
        }
    }

    private static void migrateFrom(Statement statement, int fromVersion) throws SQLException {
        // When adding version 2, replace this throw with: if (fromVersion == 1) { migrateTo2(statement); return; }
        throw new SQLException("no migration path from schema version " + fromVersion);
    }
}
