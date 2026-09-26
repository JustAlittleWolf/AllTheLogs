package me.wolfii.allthelogs.client.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.parse.PackedFormatting;

import java.io.BufferedWriter;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Writes chat lines to a downloads file. Log paths, archive entries, and session ids are left out.
 * <p>
 * Each message is formatted and appended to the file on its own. The whole document is never built in memory.
 * JSON entries are still individual Gson objects, written into a compact array one at a time.
 * <p>
 * Text is one message per line. The date and time stay readable and share one pair of square brackets.
 * JSON adds the user, server or world, formatting as ranges into the message, and whether the line is a search match.
 * JSON and CSV timestamps are ISO-8601 local date-times. CSV keeps only the timestamp and the message.
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
     * The file contains the whole message.
     */
    public record Line(ChatEntry entry, boolean match) {
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
        return save(format, lines, null);
    }

    /**
     * Writes {@code lines} straight to a new file. {@code progress} is notified as each message is appended,
     * starting at zero written. It may be null. It runs on the caller thread.
     */
    public static Path save(Format format, List<Line> lines, Consumer<ExportProgress> progress) {
        return save(DownloadFolder.resolve(), format, lines, LocalDateTime.now(), progress);
    }

    /**
     * Loaded rows already limited to the export scope.
     */
    public static List<Line> fromRows(List<DisplayRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Line> lines = new ArrayList<>(rows.size());
        for (DisplayRow row : rows) {
            if (row == null) continue;
            lines.add(new Line(row.entry(), row.match()));
        }
        return List.copyOf(lines);
    }

    /**
     * Search-query hits. Every line is a match.
     */
    public static List<Line> fromQuery(List<ChatEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<Line> lines = new ArrayList<>(entries.size());
        for (ChatEntry entry : entries) {
            if (entry == null) continue;
            lines.add(new Line(entry, true));
        }
        return List.copyOf(lines);
    }

    static Path save(Path directory, Format format, List<Line> lines, LocalDateTime exportedAt) {
        return save(directory, format, lines, exportedAt, null);
    }

    static Path save(Path directory, Format format, List<Line> lines, LocalDateTime exportedAt,
                     Consumer<ExportProgress> progress) {
        try {
            Files.createDirectories(directory);
            Path file = uniqueFile(directory, format, exportedAt);
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                write(format, lines, writer, progress);
            }
            return file;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static String render(Format format, List<Line> lines) {
        StringBuilder body = new StringBuilder();
        try {
            write(format, lines, body, null);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return body.toString();
    }

    private static void write(Format format, List<Line> lines, Appendable out, Consumer<ExportProgress> progress)
        throws IOException {
        List<Line> present = present(lines);
        int total = present.size();
        report(progress, 0, total);
        switch (format) {
            case TEXT -> writeText(present, out, progress);
            case JSON -> writeJson(present, out, progress);
            case CSV -> writeCsv(present, out, progress);
        }
    }

    private static void report(Consumer<ExportProgress> progress, int written, int total) {
        if (progress != null) progress.accept(new ExportProgress(written, total));
    }

    private static List<Line> present(List<Line> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Line> present = new ArrayList<>(lines.size());
        for (Line line : lines) {
            if (line != null && line.entry() != null) present.add(line);
        }
        return present;
    }

    /**
     * Readable date and time for the text file, without brackets.
     */
    static String readableTimestamp(LocalDateTime time) {
        if (time == null) return "";
        String text = time.format(DATED);
        if (time.getNano() == 0) return text;
        return text + "." + String.format(Locale.ROOT, "%03d", time.getNano() / 1_000_000);
    }

    /**
     * ISO-8601 local date-time for JSON and CSV, such as {@code 2026-09-26T14:03:02.123}.
     */
    static String isoTimestamp(LocalDateTime time) {
        if (time == null) return "";
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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

    private static void writeText(List<Line> lines, Appendable out, Consumer<ExportProgress> progress)
        throws IOException {
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            ChatEntry entry = lines.get(i).entry();
            out.append('[').append(readableTimestamp(entry.timestamp())).append("] ")
                .append(message(entry));
            report(progress, i + 1, lines.size());
        }
        if (!lines.isEmpty()) out.append('\n');
    }

    private static void writeJson(List<Line> lines, Appendable out, Consumer<ExportProgress> progress)
        throws IOException {
        if (lines.isEmpty()) {
            out.append("[]\n");
            return;
        }
        out.append('[');
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append(',');
            out.append(GSON.toJson(jsonObject(lines.get(i))));
            report(progress, i + 1, lines.size());
        }
        out.append("]\n");
    }

    private static JsonObject jsonObject(Line line) {
        ChatEntry entry = line.entry();
        JsonObject object = new JsonObject();
        object.addProperty("timestamp", isoTimestamp(entry.timestamp()));
        addNullable(object, "user", entry.minecraftUser());
        addNullable(object, "server", entry.serverOrWorld());
        object.addProperty("message", message(entry));
        object.addProperty("match", line.match());
        JsonArray formatting = formatting(entry.formatting());
        if (formatting != null) object.add("formatting", formatting);
        return object;
    }

    private static void writeCsv(List<Line> lines, Appendable out, Consumer<ExportProgress> progress)
        throws IOException {
        out.append("timestamp,message\n");
        for (int i = 0; i < lines.size(); i++) {
            ChatEntry entry = lines.get(i).entry();
            out.append(field(isoTimestamp(entry.timestamp()))).append(',')
                .append(field(message(entry))).append('\n');
            report(progress, i + 1, lines.size());
        }
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
