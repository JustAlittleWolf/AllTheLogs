package me.wolfii.allthelogs.data.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Records the schema version written by this build and steps an older, existing database forward to it.
 * <p>
 * A database with no version at all (predates versioning) or a version older than
 * {@link #MIGRATIONS}. A version bump that needs no data changes (a purely additive, backward-compatible
 * schema/behavior change) still gets an entry here — registered as {@code (statement) -> { }} — so a
 * missing entry always means "forgotten", never "not needed".
 */
public final class SchemaMigration {
    private static final Migration NO_OP = (_) -> {
    };

    /**
     * Step {@code v} takes a database from version {@code v} to {@code v + 1}. Applied in a loop by
     * {@link #migrate}, which advances and persists the version number one step at a time so a failure
     * partway through a multi-step migration doesn't have to be redone from the start on retry.
     */
    private static final Map<Integer, Migration> MIGRATIONS = Map.of(
        1, NO_OP,
        2, NO_OP,
        3, SchemaMigration::migrate3To4SeedClusterMarker
    );

    @FunctionalInterface
    private interface Migration {
        void apply(Statement statement) throws SQLException;
    }

    private SchemaMigration() {
    }

    /**
     * Ensures a fresh database is at {@link Schema#CURRENT_VERSION}, or steps an existing one forward to it.
     */
    public static void migrate(Statement statement) throws SQLException {
        int version = readVersion(statement);
        if (version == 0) {
            Schema.create(statement);
            setVersion(statement, Schema.CURRENT_VERSION);
            return;
        }
        if (version > Schema.CURRENT_VERSION) {
            throw new SQLException("database schema version " + version
                + " is newer than this mod supports (" + Schema.CURRENT_VERSION + ")");
        }
        if (version < 0) {
            throw new SQLException("database schema version " + version
                + " is too old to migrate; delete it and import again");
        }
        while (version < Schema.CURRENT_VERSION) {
            Migration step = MIGRATIONS.get(version);
            if (step == null) {
                throw new SQLException("no migration registered from schema version " + version
                    + " to " + (version + 1));
            }
            step.apply(statement);
            version++;
            setVersion(statement, version);
        }
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
    private static void migrate3To4SeedClusterMarker(Statement statement) throws SQLException {
        statement.execute("INSERT INTO " + Schema.META_TABLE + " VALUES ('" + Schema.CLUSTER_MARKER_KEY + "', '0')");
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
