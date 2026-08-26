package me.wolfii.allthelogs.config;

import me.wolfii.allthelogs.data.ChatQuery;
import me.wolfii.allthelogs.search.SearchFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persisted browser defaults, stored as JSON under the instance {@code .config} directory.
 */
public final class AllTheLogsSettings {
    private static final Pattern STRING = Pattern.compile("\"(contextLines|limit|caseSensitive|regex|sort)\"\\s*:\\s*(\"[^\"]*\"|true|false|-?\\d+)");

    private int contextLines = SearchFilter.DEFAULT_CONTEXT_LINES;
    private int limit = SearchFilter.DEFAULT_LIMIT;
    private boolean caseSensitive;
    private boolean regex;
    private ChatQuery.Sort sort = ChatQuery.Sort.ASCENDING;

    public static AllTheLogsSettings load(Path file) throws IOException {
        AllTheLogsSettings settings = new AllTheLogsSettings();
        if (!Files.isRegularFile(file)) {
            return settings;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = STRING.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            switch (key) {
                case "contextLines" -> settings.setContextLines(Integer.parseInt(value));
                case "limit" -> settings.setLimit(Integer.parseInt(value));
                case "caseSensitive" -> settings.setCaseSensitive(Boolean.parseBoolean(value));
                case "regex" -> settings.setRegex(Boolean.parseBoolean(value));
                case "sort" -> settings.setSort(parseSort(unquote(value)));
                default -> {
                }
            }
        }
        return settings;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static ChatQuery.Sort parseSort(String raw) {
        try {
            return ChatQuery.Sort.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ChatQuery.Sort.ASCENDING;
        }
    }

    public int contextLines() {
        return contextLines;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = Math.clamp(contextLines, 0, SearchFilter.MAX_CONTEXT_LINES);
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(1, limit);
    }

    public boolean caseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public boolean regex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public ChatQuery.Sort sort() {
        return sort;
    }

    public void setSort(ChatQuery.Sort sort) {
        this.sort = Objects.requireNonNull(sort, "sort");
    }

    public SearchFilter toFilter() {
        return SearchFilter.defaults()
            .withContextLines(contextLines)
            .withLimit(limit)
            .withCaseSensitive(caseSensitive)
            .withRegex(regex)
            .withSort(sort);
    }

    public void apply(SearchFilter filter) {
        setContextLines(filter.contextLines());
        setLimit((int) Math.min(Integer.MAX_VALUE, Math.max(1, filter.limit())));
        setCaseSensitive(filter.caseSensitive());
        setRegex(filter.regex());
        setSort(filter.sort());
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        String json = """
            {
              "contextLines": %d,
              "limit": %d,
              "caseSensitive": %s,
              "regex": %s,
              "sort": "%s"
            }
            """.formatted(contextLines, limit, caseSensitive, regex, sort.name());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }
}
