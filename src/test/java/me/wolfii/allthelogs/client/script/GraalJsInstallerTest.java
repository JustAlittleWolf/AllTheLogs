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

            List<Integer> percents = new ArrayList<>();
            installer.install(progress -> percents.add(progress.percent()));
            assertEquals(GraalJs.ARTIFACTS.size(), added.size());
            assertEquals(GraalJs.ARTIFACTS.size(), hits.get());
            assertOverallProgressSpansEveryDownload(percents);
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

    @Test
    void percentTreatsEachArtifactAsAnEqualSlice() {
        int downloads = GraalJs.ARTIFACTS.size();
        GraalJsInstaller.Progress firstHalf = GraalJsInstaller.Progress.working(
            GraalJsInstaller.Progress.Stage.DOWNLOADING, 0, downloads, 50, 100, "polyglot");
        GraalJsInstaller.Progress lastHalf = GraalJsInstaller.Progress.working(
            GraalJsInstaller.Progress.Stage.DOWNLOADING, downloads - 1, downloads, 50, 100, "js-language");
        assertTrue(firstHalf.percent() < 100 / downloads + 2, firstHalf.percent() + " should stay in the first slice");
        assertTrue(lastHalf.percent() > 90, lastHalf.percent() + " should be near the end of the overall bar");
        assertTrue(lastHalf.percent() > firstHalf.percent());
        assertEquals(100, GraalJsInstaller.Progress.ready().percent());
    }

    private static void assertOverallProgressSpansEveryDownload(List<Integer> percents) {
        assertFalse(percents.isEmpty());
        for (int i = 1; i < percents.size(); i++) {
            assertTrue(percents.get(i) >= percents.get(i - 1),
                "overall progress reset at index " + i + ": " + percents);
        }
        assertTrue(percents.getLast() >= 90, "install should finish near 100%: " + percents);
        int firstDownload = percents.stream().filter(percent -> percent > 0).findFirst().orElse(0);
        assertTrue(firstDownload <= 100 / GraalJs.ARTIFACTS.size() + 2,
            "first download should only fill its own slice: " + percents);
    }
}
