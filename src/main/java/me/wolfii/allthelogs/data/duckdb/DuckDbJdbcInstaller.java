package me.wolfii.allthelogs.data.duckdb;

import org.duckdb.DuckDBDriver;
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
 * Downloads the architecture-specific DuckDB JDBC jar into a shared cache and puts it on the classpath
 * so {@code DuckDBNative} can unpack the bundled library.
 */
public final class DuckDbJdbcInstaller implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("allthelogs");
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final Path cacheDirectory;
    private final String repository;
    private final ClassPathAppender classPath;
    private final BooleanSupplier alreadyPresent;
    private final HttpClient http;

    public DuckDbJdbcInstaller(Path cacheDirectory, String repository, ClassPathAppender classPath) {
        this(cacheDirectory, repository, classPath, DuckDbJdbcInstaller::nativeLibraryPresent);
    }

    DuckDbJdbcInstaller(Path cacheDirectory, String repository, ClassPathAppender classPath,
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

    public static boolean nativeLibraryPresent() {
        return DuckDBDriver.class.getResource("/" + DuckDbJdbc.nativeLibraryResource()) != null;
    }

    private static String sha256(Path file, Consumer<Progress> progress, String classifier, long total)
        throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                digest.update(buffer, 0, read);
                copied += read;
                notify(progress, new Progress(Progress.Stage.VERIFYING, copied, total, classifier, null));
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
     * Ensures the native library is on the classpath, downloading the platform jar when needed.
     */
    public void install(Consumer<Progress> progress) throws Exception {
        if (alreadyPresent.getAsBoolean()) {
            notify(progress, Progress.ready());
            return;
        }
        String classifier = DuckDbJdbc.classifier();
        Files.createDirectories(cacheDirectory);
        Path jar = cacheDirectory.resolve(DuckDbJdbc.jarFileName(classifier));
        if (!isValidCache(jar, classifier, progress)) {
            download(jar, classifier, progress);
        }
        notify(progress, new Progress(Progress.Stage.LOADING, Files.size(jar), Files.size(jar), classifier, null));
        classPath.add(jar);
        if (!alreadyPresent.getAsBoolean()) {
            throw new IOException("DuckDB native library was not found after adding " + jar.getFileName());
        }
        LOGGER.info("Loaded DuckDB JDBC driver");
        notify(progress, Progress.ready());
    }

    @Override
    public void close() {
        http.close();
    }

    private boolean isValidCache(Path jar, String classifier, Consumer<Progress> progress) {
        try {
            Path shaFile = shaPath(jar);
            if (!Files.isRegularFile(jar) || !Files.isRegularFile(shaFile)) {
                return false;
            }
            String expected = Files.readString(shaFile, StandardCharsets.UTF_8).trim();
            String actual = sha256(jar, progress, classifier, Files.size(jar));
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

    private void download(Path jar, String classifier, Consumer<Progress> progress) throws Exception {
        LOGGER.info("Downloading DuckDB JDBC jar ({})", classifier);
        String jarUrl = DuckDbJdbc.mavenJarUrl(repository, classifier);
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
            throw new IOException("download failed: HTTP " + response.statusCode() + " for " + classifier);
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
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("DuckDB download interrupted");
                }
                out.write(buffer, 0, read);
                copied += read;
                notify(progress, new Progress(Progress.Stage.DOWNLOADING, copied, total, classifier, null));
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
        LOGGER.info("Downloaded DuckDB JDBC jar to {}", jar);
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

    public record Progress(Stage stage, long bytes, long total, String classifier, String error) {
        public static Progress ready() {
            return new Progress(Stage.READY, 0, 0, DuckDbJdbc.classifier(), null);
        }

        public static Progress failed(String message) {
            return new Progress(Stage.FAILED, 0, 0, DuckDbJdbc.classifier(), message);
        }

        public int percent() {
            if (total <= 0) return 0;
            return (int) Math.min(100, Math.round(bytes * 100.0 / total));
        }

        public enum Stage {
            DOWNLOADING,
            VERIFYING,
            LOADING,
            READY,
            FAILED
        }
    }
}
