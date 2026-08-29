package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Records the schema version written by this build. Alpha releases do not migrate existing files:
 * a matching version opens as-is, anything else is rejected so the database can be deleted and
 * imported again.
 */
public final class SchemaMigration {
    /** Schema version written by this release. */
    public static final int CURRENT_VERSION = 3;
    static final String META_TABLE = "allthelogs_meta";
    static final String VERSION_KEY = "schema_version";

    private SchemaMigration() {
    }

    /**
     * Ensures a fresh database is at {@link #CURRENT_VERSION}, or that an existing file already is.
     */
    public static void migrate(Statement statement) throws SQLException {
        createMetaTable(statement);
        int version = readVersion(statement);
        if (version == 0) {
            if (hasDataTables(statement)) {
                throw new SQLException("database has no schema version; delete it and import again"
                    + " (this alpha build does not migrate existing files)");
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
            throw new SQLException("database schema version " + version
                + " is older than this mod (" + CURRENT_VERSION
                + "); delete the database file (this alpha build does not migrate existing files)");
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
            String value = result.getString(1);
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new SQLException("invalid schema version: " + value, e);
            }
        }
    }

    static void setVersion(Statement statement, int version) throws SQLException {
        statement.execute("DELETE FROM " + META_TABLE + " WHERE k = '" + VERSION_KEY + "'");
        statement.execute("INSERT INTO " + META_TABLE + " VALUES ('" + VERSION_KEY + "', '" + version + "')");
    }
}
