package me.wolfii.allthelogs.data.duckdb;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DuckDbJdbcTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsOsAndArchToMavenClassifier() {
        assertEquals("linux_amd64", DuckDbJdbc.classifier("Linux", "amd64"));
        assertEquals("linux_amd64", DuckDbJdbc.classifier("Linux", "x86_64"));
        assertEquals("linux_arm64", DuckDbJdbc.classifier("Linux", "aarch64"));
        assertEquals("windows_amd64", DuckDbJdbc.classifier("Windows 11", "amd64"));
        assertEquals("windows_arm64", DuckDbJdbc.classifier("Windows 11", "aarch64"));
        assertEquals("macos_universal", DuckDbJdbc.classifier("Mac OS X", "aarch64"));
        assertEquals("macos_universal", DuckDbJdbc.classifier("Mac OS X", "x86_64"));
    }

    @Test
    void nativeResourceNameMatchesDuckDbJdbcLayout() {
        assertEquals("libduckdb_java.so_linux_amd64", DuckDbJdbc.nativeLibraryResource("Linux", "amd64"));
        assertEquals("libduckdb_java.so_osx_universal", DuckDbJdbc.nativeLibraryResource("Mac OS X", "aarch64"));
        assertEquals("libduckdb_java.so_windows_amd64", DuckDbJdbc.nativeLibraryResource("Windows 10", "x86_64"));
    }

    @Test
    void cacheLivesUnderSharedDuckDbHome() {
        assertTrue(DuckDbJdbc.cacheDirectory().endsWith(Path.of(".duckdb", "jdbc", DuckDbJdbc.VERSION)));
    }

    @Test
    void downloadsArchitectureJarOnceAndReusesCache() throws Exception {
        byte[] jarBytes = "duckdb-native-jar".repeat(1000).getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jarBytes));
        String classifier = DuckDbJdbc.classifier();
        String jarName = DuckDbJdbc.jarFileName(classifier);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int[] hits = {0};
        server.createContext("/org/duckdb/duckdb_jdbc/" + DuckDbJdbc.VERSION + "/" + jarName, exchange -> {
            hits[0]++;
            exchange.sendResponseHeaders(200, jarBytes.length);
            exchange.getResponseBody().write(jarBytes);
            exchange.close();
        });
        server.createContext("/org/duckdb/duckdb_jdbc/" + DuckDbJdbc.VERSION + "/" + jarName + ".sha256", exchange -> {
            byte[] body = sha.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String repo = "http://127.0.0.1:" + server.getAddress().getPort();
            Path cache = tempDir.resolve("cache");
            List<Path> added = new ArrayList<>();
            AtomicBoolean present = new AtomicBoolean();
            DuckDbJdbcInstaller installer = new DuckDbJdbcInstaller(
                cache, repo, path -> { added.add(path); present.set(true); }, present::get);

            installer.install(progress -> {
            });
            Path cached = cache.resolve(jarName);
            assertTrue(Files.isRegularFile(cached));
            assertEquals(1, added.size());
            assertEquals(cached, added.getFirst());
            assertEquals(1, hits[0]);

            present.set(false);
            added.clear();
            installer.install(progress -> {
            });
            assertEquals(1, added.size());
            assertEquals(1, hits[0], "second install should use the cache");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipsDownloadWhenNativeLibraryIsAlreadyPresent() throws Exception {
        DuckDbJdbcInstaller installer = new DuckDbJdbcInstaller(
            tempDir, "http://127.0.0.1:1", path -> fail("should not add " + path), () -> true);
        installer.install(progress -> assertEquals(DuckDbJdbcInstaller.Progress.Stage.READY, progress.stage()));
        assertTrue(isEmpty(tempDir));
    }

    @Test
    void testRuntimeHasTheArchitectureNativeLibrary() {
        assertTrue(DuckDbJdbcInstaller.nativeLibraryPresent());
    }

    @Test
    void failedDownloadDoesNotLeaveAPartialJar() throws Exception {
        String classifier = DuckDbJdbc.classifier();
        String jarName = DuckDbJdbc.jarFileName(classifier);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/org/duckdb/duckdb_jdbc/" + DuckDbJdbc.VERSION + "/" + jarName, exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        server.start();
        try {
            String repo = "http://127.0.0.1:" + server.getAddress().getPort();
            Path cache = tempDir.resolve("cache");
            DuckDbJdbcInstaller installer = new DuckDbJdbcInstaller(
                cache, repo, path -> fail("should not add " + path), () -> false);
            assertThrows(IOException.class, () -> installer.install(progress -> {
            }));
            assertTrue(Files.notExists(cache.resolve(jarName)));
            assertTrue(Files.notExists(cache.resolve(jarName + ".part")));
        } finally {
            server.stop(0);
        }
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.findAny().isEmpty();
        }
    }
}
