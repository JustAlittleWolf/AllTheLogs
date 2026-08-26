package me.wolfii.allthelogs.data.store;

import org.duckdb.DuckDBConnection;
import org.duckdb.DuckDBDriver;

import java.nio.file.Path;
import java.sql.DriverManager;
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
    private StoreConnections() {
    }

    /**
     * Opens, and if needed creates, the database file at {@code absolutePath}.
     */
    public static DuckDBConnection openFile(Path absolutePath) throws SQLException {
        return open("jdbc:duckdb:" + absolutePath);
    }

    /**
     * Opens an in-memory database.
     */
    public static DuckDBConnection openInMemory() throws SQLException {
        return open("jdbc:duckdb:");
    }

    private static DuckDBConnection open(String url) throws SQLException {
        DuckDBConnection connection = (DuckDBConnection) DriverManager.getConnection(url, settings());
        try (Statement statement = connection.createStatement()) {
            SchemaMigration.migrate(statement);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private static Properties settings() {
        Properties settings = new Properties();
        settings.setProperty("storage_compatibility_version", "latest");
        settings.setProperty(DuckDBDriver.JDBC_STREAM_RESULTS, "true");
        return settings;
    }
}
