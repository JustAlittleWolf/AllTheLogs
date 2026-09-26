package me.wolfii.allthelogs.client.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageExportTest {
    @TempDir
    Path temp;

    @Test
    void textIsOneDatedLinePerMessageAndNothingElse() {
        String text = MessageExport.render(MessageExport.Format.TEXT, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello", null, true),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2, 123_000_000), "Notch", "hypixel.net", "café", null, true)
        ));
        assertEquals("""
            [2026-09-26 14:03:01] Hello
            [2026-09-26 14:03:02.123] café
            """, text);
        assertFalse(text.contains("Notch"));
        assertFalse(text.contains("hypixel"));
        assertFalse(text.contains("latest.log"));
    }

    @Test
    void jsonKeepsUserServerMessageFormattingAndMatchFlags() {
        long[] formatting = {PackedFormatting.run(0, 5, PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD)};
        String json = MessageExport.render(MessageExport.Format.JSON, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello there", formatting, true),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2), null, null, "plain", null, false)
        ));
        assertFalse(json.contains("latest.log"));
        assertFalse(json.contains("26.2"));
        assertFalse(json.contains("log-user"));
        assertFalse(json.contains("\n "));
        assertTrue(json.contains("},\n{"));
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        JsonArray rows = document.getAsJsonArray("messages");
        assertTrue(document.getAsJsonObject("metadata").get("query").isJsonNull());
        assertEquals(2, rows.size());
        JsonObject first = rows.get(0).getAsJsonObject();
        assertEquals("2026-09-26T14:03:01", first.get("timestamp").getAsString());
        assertEquals("Notch", first.get("user").getAsString());
        assertEquals("hypixel.net", first.get("server").getAsString());
        assertEquals("Hello there", first.get("message").getAsString());
        assertTrue(first.get("match").getAsBoolean());
        assertFalse(first.has("selected"));
        assertFalse(first.has("selection"));
        JsonArray ranges = first.getAsJsonArray("formatting");
        assertEquals(1, ranges.size());
        JsonObject range = ranges.get(0).getAsJsonObject();
        assertEquals(0, range.get("start").getAsInt());
        assertEquals(5, range.get("end").getAsInt());
        assertEquals("#FF5555", range.get("color").getAsString());
        assertTrue(range.get("bold").getAsBoolean());
        assertFalse(range.get("italic").getAsBoolean());
        assertFalse(range.get("underline").getAsBoolean());
        assertFalse(range.get("strikethrough").getAsBoolean());
        assertFalse(range.get("obfuscated").getAsBoolean());

        JsonObject plain = rows.get(1).getAsJsonObject();
        assertEquals("2026-09-26T14:03:02", plain.get("timestamp").getAsString());
        assertTrue(plain.get("user").isJsonNull());
        assertTrue(plain.get("server").isJsonNull());
        assertFalse(plain.get("match").getAsBoolean());
        assertFalse(plain.has("selected"));
        assertFalse(plain.has("formatting"));
        assertFalse(plain.has("selection"));
    }

    @Test
    void csvKeepsTimestampAndMessageOnly() {
        long[] formatting = {PackedFormatting.run(0, 5, PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD)};
        String csv = MessageExport.render(MessageExport.Format.CSV, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello", formatting, true),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2), "Alex", "world/hi", "say \"hi\", friend\nnext", null, false)
        ));
        assertEquals("""
            timestamp,message
            2026-09-26T14:03:01,Hello
            2026-09-26T14:03:02,"say ""hi"", friend
            next"
            """, csv);
        assertFalse(csv.contains("Notch"));
        assertFalse(csv.contains("Alex"));
        assertFalse(csv.contains("hypixel"));
        assertFalse(csv.contains("world/hi"));
        assertFalse(csv.contains("FF5555"));
        assertFalse(csv.contains("bold"));
        assertFalse(csv.contains("latest.log"));
        assertFalse(csv.contains("formatting"));
        assertFalse(csv.contains("selected"));
    }

    @Test
    void saveStreamsEachMessageAndMatchesTheRenderedText() throws Exception {
        long[] formatting = {PackedFormatting.run(0, 5, PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD)};
        List<MessageExport.Line> lines = new ArrayList<>();
        lines.add(line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello there", formatting, true));
        lines.add(null);
        lines.add(line(LocalDateTime.of(2026, 9, 26, 14, 3, 2), null, null, "say \"hi\", friend\nnext", null, false));
        LocalDateTime stamp = LocalDateTime.of(2026, 9, 26, 14, 3, 1);
        for (MessageExport.Format format : MessageExport.Format.values()) {
            List<ExportProgress> seen = new ArrayList<>();
            Path file = MessageExport.save(temp, format, lines, stamp, seen::add);
            assertEquals(MessageExport.render(format, lines), Files.readString(file), format.name());
            assertEquals(3, seen.size(), format.name());
            assertEquals(0, seen.getFirst().written());
            assertEquals(2, seen.getFirst().total());
            assertEquals(2, seen.getLast().written());
            assertEquals(100, seen.getLast().percent());
            for (int i = 1; i < seen.size(); i++) {
                assertTrue(seen.get(i).written() >= seen.get(i - 1).written());
            }
        }
    }

    @Test
    void exportProgressPercentTracksWrittenMessages() {
        assertEquals(0, new ExportProgress(0, 0).percent());
        assertEquals(0, new ExportProgress(0, 10).percent());
        assertEquals(50, new ExportProgress(1, 2).percent());
        assertEquals(100, new ExportProgress(2, 2).percent());
        assertEquals(0, new ExportProgress(-1, -4).percent());
    }

    @Test
    void emptyExportsStayValidAndFilesLandWithoutColliding() throws Exception {
        assertEquals("", MessageExport.render(MessageExport.Format.TEXT, List.of()));
        assertEquals("""
            {
            "messages": [],
            "metadata": {"scope":null,"query":null,"regex":false,"caseSensitive":false,"contextLines":0,"sort":null,"startingAt":null,"upUntil":null,"version":null,"server":null}
            }
            """, MessageExport.render(MessageExport.Format.JSON, List.of()));
        assertEquals("timestamp,message\n", MessageExport.render(MessageExport.Format.CSV, List.of()));

        LocalDateTime stamp = LocalDateTime.of(2026, 9, 26, 14, 3, 1);
        Path first = MessageExport.save(temp, MessageExport.Format.TEXT, List.of(
            line(stamp, null, null, "Hi", null, true)), stamp);
        Path second = MessageExport.save(temp, MessageExport.Format.TEXT, List.of(), stamp);
        assertEquals("allthelogs-2026-09-26-14-03-01-none.txt", first.getFileName().toString());
        assertEquals("allthelogs-2026-09-26-14-03-01-none-2.txt", second.getFileName().toString());
        assertEquals("[2026-09-26 14:03:01] Hi\n", Files.readString(first));
    }

    @Test
    void jsonWritesSearchMetadataAfterEachMessageOnItsOwnLine() {
        ExportMetadata metadata = new ExportMetadata("query", "Hello/there?", true, false, 3, "ascending",
            LocalDateTime.of(2026, 9, 26, 0, 0), null, "1.21.8", "hypixel");
        String json = MessageExport.render(MessageExport.Format.JSON, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello", null, true),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2), null, null, "plain", null, false)
        ), metadata);
        assertTrue(json.startsWith("""
            {
            "messages": [
            {"""));
        assertTrue(json.contains("},\n{"));
        assertFalse(json.contains("\n "));
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(2, document.getAsJsonArray("messages").size());
        JsonObject meta = document.getAsJsonObject("metadata");
        assertEquals("query", meta.get("scope").getAsString());
        assertEquals("Hello/there?", meta.get("query").getAsString());
        assertTrue(meta.get("regex").getAsBoolean());
        assertFalse(meta.get("caseSensitive").getAsBoolean());
        assertEquals(3, meta.get("contextLines").getAsInt());
        assertEquals("ascending", meta.get("sort").getAsString());
        assertEquals("2026-09-26T00:00:00", meta.get("startingAt").getAsString());
        assertTrue(meta.get("upUntil").isJsonNull());
        assertEquals("1.21.8", meta.get("version").getAsString());
        assertEquals("hypixel", meta.get("server").getAsString());
        int messagesEnd = json.indexOf("\n],\n\"metadata\": ");
        assertTrue(messagesEnd > 0);
        assertTrue(messagesEnd < json.indexOf("\"metadata\""));
    }

    @Test
    void fileNameIncludesTheSearchQueryOrNone() throws Exception {
        LocalDateTime stamp = LocalDateTime.of(2026, 9, 26, 14, 3, 1);
        ExportMetadata named = new ExportMetadata("visible", "Hello/there?", false, false, 0, null,
            null, null, null, null);
        Path queried = MessageExport.save(temp, MessageExport.Format.JSON, List.of(
            line(stamp, null, null, "Hi", null, true)), stamp, named, null);
        Path empty = MessageExport.save(temp, MessageExport.Format.CSV, List.of(), stamp, null, null);
        assertEquals("allthelogs-2026-09-26-14-03-01-Hello-there.json", queried.getFileName().toString());
        assertEquals("allthelogs-2026-09-26-14-03-01-none.csv", empty.getFileName().toString());
        assertEquals("none", ExportMetadata.fileToken("   "));
        assertEquals("none", ExportMetadata.fileToken("???"));
        assertEquals("café", ExportMetadata.fileToken("café"));
    }

    @Test
    void loadedRowsCarryMatchWithoutClippingTheMessage() {
        DisplayRow match = row("Hello there", 4, true);
        DisplayRow context = row("around", 5, false);
        List<MessageExport.Line> lines = MessageExport.fromRows(List.of(match, context));
        assertEquals(2, lines.size());
        assertEquals("Hello there", lines.get(0).entry().message());
        assertTrue(lines.get(0).match());
        assertEquals("around", lines.get(1).entry().message());
        assertFalse(lines.get(1).match());
    }

    @Test
    void queryLinesAreMatches() {
        ChatEntry hit = entry(LocalDateTime.of(2026, 9, 26, 14, 3, 1), null, null, "Hello there", null, 4);
        ChatEntry other = entry(LocalDateTime.of(2026, 9, 26, 14, 3, 2), null, null, "other", null, 9);
        List<ChatEntry> entries = new ArrayList<>();
        entries.add(hit);
        entries.add(other);
        entries.add(null);
        List<MessageExport.Line> lines = MessageExport.fromQuery(entries);
        assertEquals(2, lines.size());
        assertEquals("Hello there", lines.get(0).entry().message());
        assertTrue(lines.get(0).match());
        assertTrue(lines.get(1).match());
    }

    private static MessageExport.Line line(LocalDateTime time, String user, String server, String message,
                                           long[] formatting, boolean match) {
        return new MessageExport.Line(entry(time, user, server, message, formatting, 4), match);
    }

    private static DisplayRow row(String message, int line, boolean match) {
        LocalDateTime time = LocalDateTime.of(2026, 9, 26, 14, 3, 1);
        return new DisplayRow(entry(time, null, null, message, null, line), match, List.of());
    }

    private static ChatEntry entry(LocalDateTime time, String user, String server, String message, long[] formatting,
                                   int line) {
        ChatLog log = new ChatLog(new LogSource.File(Path.of("secret", "latest.log")), time.toLocalDate(), "26.2",
            time, time, "log-user");
        return new ChatEntry(log, time, line, message, formatting, user, server);
    }
}
