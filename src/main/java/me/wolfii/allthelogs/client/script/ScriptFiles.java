package me.wolfii.allthelogs.client.script;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Scripts live in {@code .allthelogs/scripts}. {@code example.js} (JavaScript plus a JSDoc API
 * sketch) is created the first time the scripts screen opens.
 */
public final class ScriptFiles {
    public static final String EXAMPLE = "example.js";
    private static final DateTimeFormatter OUTPUT_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ScriptFiles() {
    }

    public static void ensureExample(Path scriptsDirectory) {
        Objects.requireNonNull(scriptsDirectory, "scriptsDirectory");
        try {
            Files.createDirectories(scriptsDirectory);
            Path example = scriptsDirectory.resolve(EXAMPLE);
            if (Files.exists(example)) {
                return;
            }
            try (InputStream in = ScriptFiles.class.getResourceAsStream(EXAMPLE)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + EXAMPLE + " resource");
                }
                Files.copy(in, example);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Path> list(Path scriptsDirectory) {
        Objects.requireNonNull(scriptsDirectory, "scriptsDirectory");
        if (!Files.isDirectory(scriptsDirectory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(scriptsDirectory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(ScriptFiles::isScript)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path outputFile(Path outputDirectory, String scriptName) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        String stem = scriptName;
        int dot = scriptName.lastIndexOf('.');
        if (dot > 0) {
            stem = scriptName.substring(0, dot);
        }
        return outputDirectory.resolve(stem + "-" + LocalDateTime.now().format(OUTPUT_STAMP) + ".txt");
    }

    public static boolean nameMatches(Path script, String query) {
        if (query == null || query.isBlank()) return true;
        return script.getFileName().toString().toLowerCase(Locale.ROOT)
            .contains(query.strip().toLowerCase(Locale.ROOT));
    }

    public static boolean suggested(Path script) {
        return script.getFileName().toString().equalsIgnoreCase(EXAMPLE);
    }

    private static boolean isScript(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ts") || name.endsWith(".js");
    }
}
