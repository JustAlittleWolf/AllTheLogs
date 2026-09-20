package me.wolfii.allthelogs.client.script;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GraalJsInstallerTest {
    @TempDir
    Path tempDir;

    @Test
    void skipsDownloadWhenEngineIsAlreadyPresent() throws Exception {
        GraalJsInstaller installer = new GraalJsInstaller(
            tempDir, "http://127.0.0.1:1", path -> fail("should not add " + path), () -> true);
        installer.install(progress -> assertEquals(GraalJsInstaller.Progress.Stage.READY, progress.stage()));
        try (var stream = Files.list(tempDir)) {
            assertTrue(stream.findAny().isEmpty());
        }
    }

    @Test
    void downloadsEachArtifactOnceAndReusesCache() throws Exception {
        byte[] jarBytes = "graaljs-jar".repeat(800).getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jarBytes));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith(".sha256")) {
                byte[] body = sha.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                hits.incrementAndGet();
                exchange.sendResponseHeaders(200, jarBytes.length);
                exchange.getResponseBody().write(jarBytes);
            }
            exchange.close();
        });
        server.start();
        try {
            String repo = "http://127.0.0.1:" + server.getAddress().getPort();
            Path cache = tempDir.resolve("cache");
            List<Path> added = new ArrayList<>();
            AtomicInteger ready = new AtomicInteger();
            GraalJsInstaller installer = new GraalJsInstaller(
                cache, repo, path -> {
                added.add(path);
                if (added.size() == GraalJs.ARTIFACTS.size()) {
                    ready.set(1);
                }
            }, () -> ready.get() > 0);

            installer.install(progress -> {
            });
            assertEquals(GraalJs.ARTIFACTS.size(), added.size());
            assertEquals(GraalJs.ARTIFACTS.size(), hits.get());
            for (GraalJs.Artifact artifact : GraalJs.ARTIFACTS) {
                assertTrue(Files.isRegularFile(cache.resolve(artifact.jarFileName())));
            }

            added.clear();
            hits.set(0);
            ready.set(0);
            installer.install(progress -> {
            });
            assertEquals(GraalJs.ARTIFACTS.size(), added.size());
            assertEquals(0, hits.get(), "second install should use the cache");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedDownloadDoesNotLeaveAPartialJar() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        server.start();
        try {
            String repo = "http://127.0.0.1:" + server.getAddress().getPort();
            Path cache = tempDir.resolve("cache");
            GraalJsInstaller installer = new GraalJsInstaller(
                cache, repo, path -> fail("should not add " + path), () -> false);
            assertThrows(IOException.class, () -> installer.install(progress -> {
            }));
            if (Files.isDirectory(cache)) {
                try (var stream = Files.list(cache)) {
                    assertTrue(stream.noneMatch(path -> path.getFileName().toString().endsWith(".jar")
                        || path.getFileName().toString().endsWith(".part")));
                }
            }
        } finally {
            server.stop(0);
        }
    }
}
