package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tracks the database schema version and upgrades older files on open.
 * <p>
 * Version history:
 * <ul>
 *   <li>1 — {@link Schema} layout ({@code log_file}, {@code chat_entry}, location index).</li>
 * </ul>
 * <p>
 * To bump the schema:
 * <ol>
 *   <li>Increment {@link #CURRENT_VERSION}</li>
 *   <li>Add a {@code case} in {@link #stepFrom(int)} that migrates from the previous version</li>
 *   <li>Document the change in the version history above</li>
 * </ol>
 * Each step is applied in order, and the stored version is advanced only after that step succeeds.
 */
public final class SchemaMigration {
    /** Schema version written by this release. */
    public static final int CURRENT_VERSION = 1;
    static final String META_TABLE = "allthelogs_meta";
    static final String VERSION_KEY = "schema_version";
    /**
     * Migrations from version {@code N} to {@code N + 1}. Add a case when bumping
     * {@link #CURRENT_VERSION}.
     */
    static final UpgradePlan PLAN = SchemaMigration::stepFrom;

    private SchemaMigration() {
    }

    /**
     * Ensures the database is at {@link #CURRENT_VERSION}, creating or upgrading it as needed.
     */
    public static void migrate(Statement statement) throws SQLException {
        createMetaTable(statement);
        int version = readVersion(statement);
        if (version == 0) {
            if (!hasDataTables(statement)) {
                Schema.create(statement);
            }
            setVersion(statement, CURRENT_VERSION);
        } else if (version > CURRENT_VERSION) {
            throw new SQLException("database schema version " + version
                + " is newer than this mod supports (" + CURRENT_VERSION + ")");
        } else if (version < CURRENT_VERSION) {
            upgrade(statement, version, CURRENT_VERSION, PLAN);
        }
    }

    private static UpgradeStep stepFrom(int fromVersion) {
        return switch (fromVersion) {
            // case 1 -> SchemaMigration::migrateTo2;
            default -> null;
        };
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

    static void upgrade(Statement statement, int from, int to, UpgradePlan plan) throws SQLException {
        for (int version = from; version < to; version++) {
            UpgradeStep step = plan.stepFrom(version);
            if (step == null) {
                throw new SQLException("no migration path from schema version " + version);
            }
            step.apply(statement);
            setVersion(statement, version + 1);
        }
    }

    @FunctionalInterface
    interface UpgradeStep {
        void apply(Statement statement) throws SQLException;
    }

    @FunctionalInterface
    interface UpgradePlan {
        UpgradeStep stepFrom(int fromVersion);
    }
}
