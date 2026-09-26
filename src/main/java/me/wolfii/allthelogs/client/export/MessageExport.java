package me.wolfii.allthelogs.client.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.parse.PackedFormatting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Writes chat lines to a downloads file. Log paths, archive entries, and session ids are left out.
 * <p>
 * Text is one message per line, each prefixed with a dated timestamp. JSON is compact, one array, and
 * adds the user, server or world, formatting as ranges into the message, whether the line is a search
 * match, and whether that whole message is part of the current selection. CSV keeps only the timestamp
 * and the message.
 * A selection exports each touched message in full, not the highlighted substring.
 */
public final class MessageExport {
    private static final DateTimeFormatter DATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .serializeNulls()
        .create();
    private static volatile ExecutorService executor;

    private MessageExport() {
    }

    public enum Format {
        TEXT("txt"),
        JSON("json"),
        CSV("csv");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    /**
     * One exported message. {@code match} is false for a context line around a search hit.
     * {@code selected} is true when the current selection covers this message; the file still contains
     * the whole message.
     */
    public record Line(ChatEntry entry, boolean match, boolean selected) {
    }

    /**
     * Background writer for export files, so a large search does not freeze the client thread.
     */
    public static Executor executor() {
        ExecutorService current = executor;
        if (current != null) return current;
        synchronized (MessageExport.class) {
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "allthelogs-export");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return executor;
        }
    }

    public static Path save(Format format, List<Line> lines) {
        return save(DownloadFolder.resolve(), format, lines, LocalDateTime.now());
    }

    /**
     * Loaded rows already limited to the export scope. A row is marked selected when its key is in
     * {@code selectedKeys}.
     */
    public static List<Line> fromRows(List<DisplayRow> rows, Set<DisplayRow.RowKey> selectedKeys) {
        if (rows == null || rows.isEmpty()) return List.of();
        Set<DisplayRow.RowKey> keys = selectedKeys == null ? Set.of() : selectedKeys;
        List<Line> lines = new ArrayList<>(rows.size());
        for (DisplayRow row : rows) {
            if (row == null) continue;
            lines.add(new Line(row.entry(), row.match(), keys.contains(row.key())));
        }
        return List.copyOf(lines);
    }

    /**
     * Search-query hits. Every line is a match; one is selected only when a loaded row with the same
     * source and line is in the current selection.
     */
    public static List<Line> fromQuery(List<ChatEntry> entries, Set<DisplayRow.RowKey> selectedKeys) {
        if (entries == null || entries.isEmpty()) return List.of();
        Set<DisplayRow.RowKey> keys = selectedKeys == null ? Set.of() : selectedKeys;
        List<Line> lines = new ArrayList<>(entries.size());
        for (ChatEntry entry : entries) {
            if (entry == null) continue;
            DisplayRow.RowKey key = new DisplayRow.RowKey(entry.chatLog().source(), entry.lineIndex());
            lines.add(new Line(entry, true, keys.contains(key)));
        }
        return List.copyOf(lines);
    }

    static Path save(Path directory, Format format, List<Line> lines, LocalDateTime exportedAt) {
        try {
            Files.createDirectories(directory);
            Path file = uniqueFile(directory, format, exportedAt);
            Files.writeString(file, render(format, lines), StandardCharsets.UTF_8);
            return file;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static String render(Format format, List<Line> lines) {
        List<Line> present = present(lines);
        return switch (format) {
            case TEXT -> text(present);
            case JSON -> json(present);
            case CSV -> csv(present);
        };
    }

    private static List<Line> present(List<Line> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Line> present = new ArrayList<>(lines.size());
        for (Line line : lines) {
            if (line != null && line.entry() != null) present.add(line);
        }
        return present;
    }

    static String timestamp(LocalDateTime time) {
        if (time == null) return "";
        String text = time.format(DATED);
        if (time.getNano() == 0) return text;
        return text + "." + String.format(Locale.ROOT, "%03d", time.getNano() / 1_000_000);
    }

    private static Path uniqueFile(Path directory, Format format, LocalDateTime exportedAt) {
        String stamp = exportedAt == null ? "export" : exportedAt.format(FILE_STAMP);
        String extension = format.extension();
        Path candidate = directory.resolve("allthelogs-" + stamp + "." + extension);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve("allthelogs-" + stamp + "-" + suffix + "." + extension);
            suffix++;
        }
        return candidate;
    }

    private static String text(List<Line> lines) {
        if (lines.isEmpty()) return "";
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) body.append('\n');
            ChatEntry entry = lines.get(i).entry();
            body.append(timestamp(entry.timestamp()))
                .append(' ')
                .append(message(entry));
        }
        return body.append('\n').toString();
    }

    private static String json(List<Line> lines) {
        JsonArray array = new JsonArray();
        for (Line line : lines) {
            ChatEntry entry = line.entry();
            JsonObject object = new JsonObject();
            object.addProperty("timestamp", timestamp(entry.timestamp()));
            addNullable(object, "user", entry.minecraftUser());
            addNullable(object, "server", entry.serverOrWorld());
            object.addProperty("message", message(entry));
            object.addProperty("match", line.match());
            object.addProperty("selected", line.selected());
            JsonArray formatting = formatting(entry.formatting());
            if (formatting != null) object.add("formatting", formatting);
            array.add(object);
        }
        String rendered = GSON.toJson(array);
        return rendered.endsWith("\n") ? rendered : rendered + "\n";
    }

    private static String csv(List<Line> lines) {
        StringBuilder body = new StringBuilder("timestamp,message\n");
        for (Line line : lines) {
            ChatEntry entry = line.entry();
            body.append(field(timestamp(entry.timestamp()))).append(',')
                .append(field(message(entry))).append('\n');
        }
        return body.toString();
    }

    private static void addNullable(JsonObject object, String key, String value) {
        if (value == null) object.add(key, JsonNull.INSTANCE);
        else object.addProperty(key, value);
    }

    /**
     * Packed formatting runs as inclusive-start exclusive-end ranges. Colour is omitted when the run has none.
     */
    private static JsonArray formatting(long[] packed) {
        if (packed == null || packed.length == 0) return null;
        JsonArray ranges = new JsonArray();
        for (long run : packed) {
            JsonObject range = new JsonObject();
            int start = PackedFormatting.offset(run);
            range.addProperty("start", start);
            range.addProperty("end", start + PackedFormatting.count(run));
            int format = PackedFormatting.format(run);
            if (PackedFormatting.hasColor(format)) {
                range.addProperty("color", String.format(Locale.ROOT, "#%06X", PackedFormatting.rgb(format)));
            }
            range.addProperty("bold", PackedFormatting.bold(format));
            range.addProperty("italic", PackedFormatting.italic(format));
            range.addProperty("underline", PackedFormatting.underline(format));
            range.addProperty("strikethrough", PackedFormatting.strikethrough(format));
            range.addProperty("obfuscated", PackedFormatting.obfuscated(format));
            ranges.add(range);
        }
        return ranges;
    }

    private static String message(ChatEntry entry) {
        if (entry == null || entry.message() == null) return "";
        return entry.message();
    }

    private static String field(String value) {
        if (value == null || value.isEmpty()) return "";
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!quote) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
