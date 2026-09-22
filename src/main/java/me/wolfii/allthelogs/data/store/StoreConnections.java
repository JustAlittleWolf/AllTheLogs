package me.wolfii.allthelogs.data.store;

import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDriver;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Opens DuckDB connections with the storage settings this store needs.
 * {@code storage_compatibility_version=latest} enables {@code DICT_FSST} string compression;
 * {@link DuckDBDriver#JDBC_STREAM_RESULTS} streams query chunks instead of materialising the
 * whole result first.
 */
public final class StoreConnections {
    private static final DuckDBDriver DRIVER = new DuckDBDriver();

    private StoreConnections() {
    }

    /**
     * Opens, and if needed creates, the database file at {@code absolutePath}.
     * When an on-disk file still needs a schema upgrade, the database file is copied first.
     */
    public static DuckDBConnection openFile(Path absolutePath) throws SQLException {
        DuckDBConnection connection = connect("jdbc:duckdb:" + absolutePath);
        try {
            int version = schemaVersion(connection);
            if (version > 0 && version < Schema.CURRENT_VERSION) {
                checkpoint(connection);
                connection.close();
                connection = null;
                backupDatabase(absolutePath, version);
                connection = connect("jdbc:duckdb:" + absolutePath);
            }
            migrate(connection);
            return connection;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
    }

    /**
     * Opens an in-memory database.
     */
    public static DuckDBConnection openInMemory() throws SQLException {
        DuckDBConnection connection = connect("jdbc:duckdb:");
        try {
            migrate(connection);
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    /**
     * Connects through {@link DuckDBDriver} instead of {@code DriverManager}. Fabric's Knot
     * classloader is a named module; {@code DriverManager.getConnection} then rejects the
     * already-registered DuckDB driver ({@code No suitable driver found for jdbc:duckdb:}).
     */
    private static DuckDBConnection connect(String url) throws SQLException {
        Connection raw = DRIVER.connect(url, settings());
        if (raw == null) {
            throw new SQLException("DuckDB driver rejected URL: " + url);
        }
        return (DuckDBConnection) raw;
    }

    private static void backupDatabase(Path database, int schemaVersion) throws SQLException {
        try {
            DatabaseBackup.copyBeforeMigration(database, schemaVersion);
        } catch (IOException e) {
            throw new SQLException("could not back up the log database before migrating schema version "
                + schemaVersion, e);
        }
    }

    private static int schemaVersion(DuckDBConnection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return SchemaMigration.readVersion(statement);
        }
    }

    private static void checkpoint(DuckDBConnection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CHECKPOINT");
        }
    }

    private static void migrate(DuckDBConnection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            SchemaMigration.migrate(statement);
        }
    }

    private static Properties settings() {
        Properties settings = new Properties();
        settings.setProperty("storage_compatibility_version", "latest");
        settings.setProperty(DuckDBDriver.JDBC_STREAM_RESULTS, "true");
        return settings;
    }
}
