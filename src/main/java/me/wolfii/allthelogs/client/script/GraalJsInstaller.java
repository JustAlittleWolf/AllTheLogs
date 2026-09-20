package me.wolfii.allthelogs.client.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Downloads GraalJS and Truffle jars into a shared cache and puts them on the classpath.
 */
public final class GraalJsInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger("allthelogs");
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final Path cacheDirectory;
    private final String repository;
    private final ClassPathAppender classPath;
    private final BooleanSupplier alreadyPresent;
    private final HttpClient http;

    public GraalJsInstaller(Path cacheDirectory, String repository, ClassPathAppender classPath) {
        this(cacheDirectory, repository, classPath, GraalJs::enginePresent);
    }

    GraalJsInstaller(Path cacheDirectory, String repository, ClassPathAppender classPath,
                     BooleanSupplier alreadyPresent) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.classPath = Objects.requireNonNull(classPath, "classPath");
        this.alreadyPresent = Objects.requireNonNull(alreadyPresent, "alreadyPresent");
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    private static String sha256(Path file, Consumer<Progress> progress, int index, int downloads, String artifact)
        throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = Files.size(file);
        long copied = 0;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                digest.update(buffer, 0, read);
                copied += read;
                notify(progress, Progress.working(Progress.Stage.VERIFYING, index, downloads, copied, size, artifact));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path shaPath(Path jar) {
        return jar.resolveSibling(jar.getFileName() + ".sha256");
    }

    private static void notify(Consumer<Progress> progress, Progress snapshot) {
        if (progress != null) {
            progress.accept(snapshot);
        }
    }

    /**
     * Ensures GraalJS is on the classpath, downloading jars when needed.
     */
    public void install(Consumer<Progress> progress) throws Exception {
        if (alreadyPresent.getAsBoolean()) {
            notify(progress, Progress.ready());
            return;
        }
        Files.createDirectories(cacheDirectory);
        int downloads = GraalJs.ARTIFACTS.size();
        notify(progress, Progress.working(Progress.Stage.LOADING, 0, downloads, 0, 0, "graaljs"));
        for (int index = 0; index < downloads; index++) {
            GraalJs.Artifact artifact = GraalJs.ARTIFACTS.get(index);
            Path jar = cacheDirectory.resolve(artifact.jarFileName());
            if (!isValidCache(jar, artifact.name(), progress, index, downloads)) {
                download(jar, artifact, progress, index, downloads);
            }
            notify(progress, Progress.working(Progress.Stage.LOADING, index + 1, downloads, 0, 0, artifact.name()));
            classPath.add(jar);
        }
        if (!alreadyPresent.getAsBoolean()) {
            throw new IOException("GraalJS was not found after adding " + cacheDirectory);
        }
        LOGGER.info("Loaded GraalJS {}", GraalJs.VERSION);
        notify(progress, Progress.ready());
    }

    private boolean isValidCache(Path jar, String artifact, Consumer<Progress> progress, int index, int downloads) {
        try {
            Path shaFile = shaPath(jar);
            if (!Files.isRegularFile(jar) || !Files.isRegularFile(shaFile)) {
                return false;
            }
            String expected = Files.readString(shaFile, StandardCharsets.UTF_8).trim();
            String actual = sha256(jar, progress, index, downloads, artifact);
            if (!expected.equalsIgnoreCase(actual)) {
                Files.deleteIfExists(jar);
                Files.deleteIfExists(shaFile);
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void download(Path jar, GraalJs.Artifact artifact, Consumer<Progress> progress, int index, int downloads)
        throws Exception {
        LOGGER.info("Downloading GraalJS jar {}", artifact.jarFileName());
        String jarUrl = GraalJs.mavenJarUrl(repository, artifact);
        notify(progress, Progress.working(Progress.Stage.DOWNLOADING, index, downloads, 0, 1, artifact.name()));
        String expectedSha = fetchSha256(jarUrl + ".sha256");
        Path part = jar.resolveSibling(jar.getFileName() + ".part");
        Files.deleteIfExists(part);
        HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl))
            .timeout(TIMEOUT)
            .header("User-Agent", "AllTheLogs")
            .GET()
            .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("download failed: HTTP " + response.statusCode() + " for " + artifact.jarFileName());
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        try (InputStream body = new DigestInputStream(response.body(), digest);
             OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = body.read(buffer)) >= 0) {
                if (read == 0) continue;
                out.write(buffer, 0, read);
                copied += read;
                notify(progress, Progress.working(
                    Progress.Stage.DOWNLOADING, index, downloads, copied, total, artifact.name()));
            }
        } catch (Exception e) {
            Files.deleteIfExists(part);
            throw e;
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!expectedSha.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(part);
            throw new IOException("SHA-256 mismatch for " + jar.getFileName());
        }
        Files.move(part, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(shaPath(jar), actual, StandardCharsets.UTF_8);
        LOGGER.info("Downloaded GraalJS jar to {}", jar);
    }

    private String fetchSha256(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("User-Agent", "AllTheLogs")
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("checksum download failed: HTTP " + response.statusCode());
        }
        String body = response.body().trim();
        String hash = body.split("\\s+")[0];
        if (hash.length() != 64) {
            throw new IOException("unexpected SHA-256 checksum: " + body);
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface ClassPathAppender {
        void add(Path jar) throws Exception;
    }

    /**
     * {@code completed} is how many of {@code downloads} artifacts are already finished.
     * {@code bytes}/{@code total} fill the current artifact's slice of the overall bar.
     */
    public record Progress(Stage stage, int completed, int downloads, long bytes, long total, String artifact,
                           String error) {
        public static Progress ready() {
            return new Progress(Stage.READY, 1, 1, 1, 1, "graaljs", null);
        }

        public static Progress idle() {
            return new Progress(Stage.IDLE, 0, 0, 0, 0, "graaljs", null);
        }

        public static Progress failed(String message) {
            return new Progress(Stage.FAILED, 0, 0, 0, 0, "graaljs", message);
        }

        static Progress working(Stage stage, int completed, int downloads, long bytes, long total, String artifact) {
            return new Progress(stage, completed, downloads, bytes, total, artifact, null);
        }

        /**
         * Overall percent across every artifact. Each download is one equal slice; known
         * Content-Length fills the current slice.
         */
        public int percent() {
            if (stage == Stage.READY) return 100;
            if (downloads <= 0) return 0;
            double intra = total > 0 ? Math.min(1.0, bytes / (double) total) : 0.0;
            return (int) Math.min(100, Math.round((completed + intra) * 100.0 / downloads));
        }

        public enum Stage {
            IDLE,
            DOWNLOADING,
            VERIFYING,
            LOADING,
            READY,
            FAILED
        }
    }
}
