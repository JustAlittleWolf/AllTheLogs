package me.wolfii.allthelogs.locations;

import me.wolfii.allthelogs.data.ImportOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in shortcuts for directories where Minecraft launchers keep logs. Path templates are expanded with
 * {@link #expand(String, Map)}; add more {@link Location} values to {@link #defaults()} as new launchers are mapped.
 */
public final class CommonLogLocations {
    private CommonLogLocations() {
    }

    /**
     * A launcher (or vanilla) log source. {@code pathTemplates} are tried in order; the first existing path is the
     * default selection. {@link #suggestedOptions()} are applied to the import form when the user picks this location.
     *
     * @param id            stable id, e.g. {@code vanilla}
     * @param displayName   shown in the import screen
     * @param pathTemplates OS-aware templates using {@code ${HOME}}, {@code ${APPDATA}}, {@code ${LOCALAPPDATA}},
     *                      {@code ${XDG_DATA_HOME}}
     * @param pathMatcher   glob relative to the selected path, or {@code null} for every file
     * @param recursive     whether to walk subdirectories
     * @param nestedArchives whether to open archives found while walking
     */
    public record Location(
        String id,
        String displayName,
        List<String> pathTemplates,
        String pathMatcher,
        boolean recursive,
        boolean nestedArchives
    ) {
        public Location {
            pathTemplates = List.copyOf(pathTemplates);
        }

        public ImportOptions suggestedOptions() {
            return ImportOptions.defaults()
                .withRecursive(recursive)
                .withNestedArchives(nestedArchives)
                .withPathMatcher(pathMatcher)
                .withSkipAlreadyImported(true);
        }

        public List<Path> resolveAll() {
            return resolveAll(environmentVariables());
        }

        public List<Path> resolveAll(Map<String, String> variables) {
            List<Path> paths = new ArrayList<>();
            for (String template : pathTemplates) {
                paths.add(Path.of(expand(template, variables)));
            }
            return List.copyOf(paths);
        }

        public Optional<Path> firstExisting() {
            return firstExisting(environmentVariables(), Files::exists);
        }

        public Optional<Path> firstExisting(Map<String, String> variables, java.util.function.Predicate<Path> exists) {
            for (Path path : resolveAll(variables)) {
                if (exists.test(path)) {
                    return Optional.of(path);
                }
            }
            return Optional.empty();
        }
    }

    public static List<Location> defaults() {
        return List.of();
    }

    public static Map<String, String> environmentVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        String home = System.getProperty("user.home", "");
        variables.put("HOME", home);
        variables.put("USERPROFILE", firstNonBlank(System.getenv("USERPROFILE"), home));
        variables.put("APPDATA", firstNonBlank(System.getenv("APPDATA"), defaultAppData(home)));
        variables.put("LOCALAPPDATA", firstNonBlank(System.getenv("LOCALAPPDATA"), defaultLocalAppData(home)));
        variables.put("XDG_DATA_HOME", firstNonBlank(System.getenv("XDG_DATA_HOME"), home + "/.local/share"));
        return Map.copyOf(variables);
    }

    public static String expand(String template, Map<String, String> variables) {
        String expanded = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            expanded = expanded.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return expanded.replace('\\', '/');
    }

    private static String defaultAppData(String home) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return home + "/AppData/Roaming";
        }
        if (os.contains("mac")) {
            return home + "/Library/Application Support";
        }
        return home;
    }

    private static String defaultLocalAppData(String home) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return home + "/AppData/Local";
        }
        return home;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
