package me.wolfii.allthelogs.data.store;

import org.duckdb.DuckDBConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Post-import maintenance. Clustering rewrites {@code chat_entry} oldest-first so DuckDB row-group
 * zone maps skip data on time-range scans. Compacting copies the live catalog into a fresh file so
 * space left behind by {@code DROP TABLE} / {@code DELETE} is actually released; DuckDB's
 * {@code VACUUM} does not shrink the file.
 */
public final class StoreOptimizer {
    private static final String COMPACT_ALIAS = "allthelogs_compact";

    private StoreOptimizer() {
    }

    /**
     * Updates planner statistics and flushes the WAL. Safe on in-memory stores.
     */
    public static void analyzeAndCheckpoint(Statement statement) throws SQLException {
        statement.execute("ANALYZE chat_entry");
        statement.execute("CHECKPOINT");
    }

    /**
     * Copies the open catalog into a new file, then replaces {@code databasePath} with that copy.
     * Closes {@code connection}; the returned connection is the replacement, already migrated.
     * When the file swap fails, the original file is restored when possible and reopened.
     */
    public static DuckDBConnection replaceWithCompactCopy(DuckDBConnection connection, Path databasePath)
        throws SQLException, IOException {
        Path compactPath = sidecar(databasePath, ".compact");
        Path compactWal = walPath(compactPath);
        Files.deleteIfExists(compactPath);
        Files.deleteIfExists(compactWal);

        try {
            copyCatalogTo(connection, compactPath);
        } catch (SQLException e) {
            deleteQuietly(compactPath);
            deleteQuietly(compactWal);
            throw e;
        }

        connection.close();

        try {
            swapCompactFile(databasePath, compactPath);
        } catch (IOException e) {
            deleteQuietly(compactPath);
            deleteQuietly(compactWal);
            try {
                return StoreConnections.openFile(databasePath);
            } catch (SQLException openFailed) {
                e.addSuppressed(openFailed);
                throw e;
            }
        }
        return StoreConnections.openFile(databasePath);
    }

    private static void copyCatalogTo(DuckDBConnection connection, Path compactPath) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CHECKPOINT");
            String source = currentDatabase(statement);
            statement.execute("ATTACH " + sqlLiteral(compactPath) + " AS " + COMPACT_ALIAS
                + " (STORAGE_VERSION 'latest')");
            try {
                statement.execute("COPY FROM DATABASE " + quoteIdent(source) + " TO " + COMPACT_ALIAS);
                statement.execute("CHECKPOINT " + COMPACT_ALIAS);
            } finally {
                statement.execute("DETACH " + COMPACT_ALIAS);
            }
        }
    }

    private static void swapCompactFile(Path databasePath, Path compactPath) throws IOException {
        Path backupPath = sidecar(databasePath, ".bak");
        Path wal = walPath(databasePath);
        Path compactWal = walPath(compactPath);
        Files.deleteIfExists(backupPath);
        Files.move(databasePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(compactPath, databasePath);
            Files.deleteIfExists(wal);
            Files.deleteIfExists(compactWal);
            Files.deleteIfExists(backupPath);
        } catch (IOException e) {
            try {
                if (Files.exists(backupPath)) {
                    Files.move(backupPath, databasePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException restoreFailed) {
                e.addSuppressed(restoreFailed);
            }
            throw e;
        }
    }

    static Path walPath(Path database) {
        return sidecar(database, ".wal");
    }

    private static Path sidecar(Path database, String suffix) {
        return database.resolveSibling(database.getFileName().toString() + suffix);
    }

    private static String currentDatabase(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT current_database()")) {
            result.next();
            return result.getString(1);
        }
    }

    private static String sqlLiteral(Path path) {
        return "'" + path.toAbsolutePath().normalize().toString().replace("'", "''") + "'";
    }

    private static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
