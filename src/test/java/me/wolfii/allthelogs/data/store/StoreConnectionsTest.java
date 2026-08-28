package me.wolfii.allthelogs.data.store;

import org.duckdb.DuckDBConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreConnectionsTest {
    @TempDir
    Path tempDir;

    @Test
    void opensWhenDriverManagerCannotSeeDuckdb() throws SQLException {
        List<Driver> removed = new ArrayList<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getName().startsWith("org.duckdb.")) {
                DriverManager.deregisterDriver(driver);
                removed.add(driver);
            }
        }
        try {
            assertThrows(SQLException.class, () -> DriverManager.getConnection("jdbc:duckdb:"));
            try (DuckDBConnection connection = StoreConnections.openFile(tempDir.resolve("logs.duckdb"));
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        } finally {
            for (Driver driver : removed) {
                DriverManager.registerDriver(driver);
            }
        }
    }
}
