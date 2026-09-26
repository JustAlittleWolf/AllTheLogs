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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MessageExportTest {
    @TempDir
    Path temp;

    @Test
    void textIsOneDatedLinePerMessageAndNothingElse() {
        String text = MessageExport.render(MessageExport.Format.TEXT, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello", null, true, false),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2, 123_000_000), "Notch", "hypixel.net", "café", null, true, true)
        ));
        assertEquals("""
            2026-09-26 14:03:01 Hello
            2026-09-26 14:03:02.123 café
            """, text);
        assertFalse(text.contains("Notch"));
        assertFalse(text.contains("hypixel"));
        assertFalse(text.contains("latest.log"));
    }

    @Test
    void jsonKeepsUserServerMessageFormattingAndMatchFlags() {
        long[] formatting = {PackedFormatting.run(0, 5, PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD)};
        String json = MessageExport.render(MessageExport.Format.JSON, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello there", formatting, true, true),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2), null, null, "plain", null, false, false)
        ));
        assertFalse(json.contains("latest.log"));
        assertFalse(json.contains("26.2"));
        assertFalse(json.contains("log-user"));
        JsonArray rows = JsonParser.parseString(json).getAsJsonArray();
        assertEquals(2, rows.size());
        JsonObject first = rows.get(0).getAsJsonObject();
        assertEquals("2026-09-26 14:03:01", first.get("timestamp").getAsString());
        assertEquals("Notch", first.get("user").getAsString());
        assertEquals("hypixel.net", first.get("server").getAsString());
        assertEquals("Hello there", first.get("message").getAsString());
        assertTrue(first.get("match").getAsBoolean());
        assertTrue(first.get("selected").getAsBoolean());
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
        assertTrue(plain.get("user").isJsonNull());
        assertTrue(plain.get("server").isJsonNull());
        assertFalse(plain.get("match").getAsBoolean());
        assertFalse(plain.get("selected").getAsBoolean());
        assertFalse(plain.has("formatting"));
        assertFalse(plain.has("selection"));
    }

    @Test
    void csvKeepsTimestampAndMessageOnly() {
        long[] formatting = {PackedFormatting.run(0, 5, PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD)};
        String csv = MessageExport.render(MessageExport.Format.CSV, List.of(
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 1), "Notch", "hypixel.net", "Hello", formatting, true, true),
            line(LocalDateTime.of(2026, 9, 26, 14, 3, 2), "Alex", "world/hi", "say \"hi\", friend\nnext", null, false, false)
        ));
        assertEquals("""
            timestamp,message
            2026-09-26 14:03:01,Hello
            2026-09-26 14:03:02,"say ""hi"", friend
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
    void emptyExportsStayValidAndFilesLandWithoutColliding() throws Exception {
        assertEquals("", MessageExport.render(MessageExport.Format.TEXT, List.of()));
        assertEquals("[]\n", MessageExport.render(MessageExport.Format.JSON, List.of()));
        assertEquals("timestamp,message\n", MessageExport.render(MessageExport.Format.CSV, List.of()));

        LocalDateTime stamp = LocalDateTime.of(2026, 9, 26, 14, 3, 1);
        Path first = MessageExport.save(temp, MessageExport.Format.TEXT, List.of(
            line(stamp, null, null, "Hi", null, true, false)), stamp);
        Path second = MessageExport.save(temp, MessageExport.Format.TEXT, List.of(), stamp);
        assertEquals("allthelogs-2026-09-26-14-03-01.txt", first.getFileName().toString());
        assertEquals("allthelogs-2026-09-26-14-03-01-2.txt", second.getFileName().toString());
        assertEquals("2026-09-26 14:03:01 Hi\n", Files.readString(first));
    }

    @Test
    void loadedRowsCarryMatchAndSelectionWithoutClippingTheMessage() {
        DisplayRow match = row("Hello there", 4, true);
        DisplayRow context = row("around", 5, false);
        List<MessageExport.Line> lines = MessageExport.fromRows(List.of(match, context), Set.of(match.key()));
        assertEquals(2, lines.size());
        assertEquals("Hello there", lines.get(0).entry().message());
        assertTrue(lines.get(0).match());
        assertTrue(lines.get(0).selected());
        assertEquals("around", lines.get(1).entry().message());
        assertFalse(lines.get(1).match());
        assertFalse(lines.get(1).selected());
    }

    @Test
    void queryLinesAreMatchesAndSelectedOnlyForTheLoadedSelection() {
        ChatEntry hit = entry(LocalDateTime.of(2026, 9, 26, 14, 3, 1), null, null, "Hello there", null, 4);
        ChatEntry other = entry(LocalDateTime.of(2026, 9, 26, 14, 3, 2), null, null, "other", null, 9);
        DisplayRow.RowKey selected = new DisplayRow.RowKey(hit.chatLog().source(), hit.lineIndex());
        List<ChatEntry> entries = new ArrayList<>();
        entries.add(hit);
        entries.add(other);
        entries.add(null);
        List<MessageExport.Line> lines = MessageExport.fromQuery(entries, Set.of(selected));
        assertEquals(2, lines.size());
        assertEquals("Hello there", lines.get(0).entry().message());
        assertTrue(lines.get(0).match());
        assertTrue(lines.get(0).selected());
        assertTrue(lines.get(1).match());
        assertFalse(lines.get(1).selected());
    }

    private static MessageExport.Line line(LocalDateTime time, String user, String server, String message,
                                           long[] formatting, boolean match, boolean selected) {
        return new MessageExport.Line(entry(time, user, server, message, formatting, 4), match, selected);
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
