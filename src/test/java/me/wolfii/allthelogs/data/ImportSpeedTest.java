package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.parse.LogParser;
import me.wolfii.allthelogs.data.parse.ParsedLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the import hot path against regex backtracking and keeps a throughput floor.
 */
class ImportSpeedTest {
    private static final int CHAT_LINES = 40_000;

    @TempDir
    Path tempDir;

    @Test
    void commaFilledConnectingChatDoesNotBacktrack() {
        String chat = "[12:00:00] [Render thread/INFO]: [CHAT] Connecting to "
            + "hello, world, ".repeat(400) + "the end";
        Pattern greedy = Pattern.compile("Connecting to (.+), (\\d+)\\s*$");
        Pattern bounded = Pattern.compile("Connecting to ([^,]+), (\\d+)\\s*$");

        long greedyNanos = timePattern(greedy, chat, 3);
        long boundedNanos = timePattern(bounded, chat, 3);
        System.out.printf("connecting chat: greedy=%.1fms bounded=%.1fms%n",
            greedyNanos / 1_000_000.0, boundedNanos / 1_000_000.0);

        assertTrue(boundedNanos < TimeUnit.MILLISECONDS.toNanos(50),
            "bounded connecting pattern took " + boundedNanos + " ns");
        assertTrue(greedyNanos > boundedNanos,
            "expected the old greedy host capture to be slower, greedy=" + greedyNanos
                + " bounded=" + boundedNanos);
    }

    @Test
    void parsesTensOfThousandsOfChatLinesQuickly() throws IOException {
        String log = typicalLog(CHAT_LINES);
        String[] lines = log.split("\n", -1);
        long regexNanos = timeRegexTimestamps(lines);
        long handNanos = timeHandTimestamps(lines);
        System.out.printf("timestamp prefix on %d lines: regex=%.1fms hand=%.1fms%n",
            lines.length, regexNanos / 1_000_000.0, handNanos / 1_000_000.0);

        ParsedLog first = parse(log);
        long started = System.nanoTime();
        ParsedLog parsed = parse(log);
        long elapsed = System.nanoTime() - started;
        System.out.printf("parsed %d chat lines in %.1fms%n", CHAT_LINES, elapsed / 1_000_000.0);

        assertEquals(first.entries().size(), parsed.entries().size());
        assertEquals(CHAT_LINES, parsed.entries().size());
        assertEquals("unicacity.eu", parsed.serverOrWorld());
        assertEquals("JustAlittleWolf", parsed.minecraftUser());
        assertEquals("26.2", parsed.minecraftVersion());
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(2),
            "parsing " + CHAT_LINES + " lines took " + elapsed + " ns");
        assertTrue(handNanos < regexNanos,
            "hand parser should beat regex, regex=" + regexNanos + " hand=" + handNanos);
    }

    @Test
    void importsManyFilesFasterThanASecondOfWorkPerThousandLines() throws IOException {
        Path logs = tempDir.resolve("logs");
        Files.createDirectories(logs);
        int files = 8;
        int linesEach = 5_000;
        for (int file = 0; file < files; file++) {
            Files.writeString(logs.resolve("2026-08-2" + file + "-1.log"), typicalLog(linesEach),
                StandardCharsets.UTF_8);
        }

        long started = System.nanoTime();
        try (LogStore store = LogStore.open(tempDir.resolve("import.duckdb"))) {
            ImportResult result = store.importDirectory(tempDir,
                ImportOptions.defaults().withOptimize(false));
            assertEquals(files, result.importedFiles());
            assertEquals((long) files * linesEach, result.importedEntries());
            assertTrue(result.failures().isEmpty(), () -> result.failures().toString());
        }
        long elapsed = System.nanoTime() - started;
        System.out.printf("imported %d files / %d lines in %.1fms%n",
            files, files * linesEach, elapsed / 1_000_000.0);
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(15),
            "import took " + elapsed + " ns");
    }

    @Test
    void skipsVersionRegexesOnUnrelatedMinecraftMentions() throws IOException {
        StringBuilder log = new StringBuilder(20_000 * 90);
        log.append("[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");
        log.append("[10:00:01] [Render thread/INFO]: Setting user: JustAlittleWolf\n");
        log.append("[10:00:02] [Render thread/INFO]: Connecting to unicacity.eu, 25565\n");
        for (int i = 0; i < 20_000; i++) {
            log.append("[10:00:03] [main/INFO]: Loading mixin minecraft/client/renderer/chunk");
            log.append(i);
            log.append(" from fabric-rendering\n");
        }
        log.append("[10:00:10] [Render thread/INFO]: [CHAT] hello\n");
        String text = log.toString();
        parse(text);
        long started = System.nanoTime();
        ParsedLog parsed = parse(text);
        long elapsed = System.nanoTime() - started;
        System.out.printf("parsed 20000 mixin lines in %.1fms%n", elapsed / 1_000_000.0);
        assertEquals("26.2", parsed.minecraftVersion());
        assertEquals(1, parsed.entries().size());
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(400),
            "unrelated minecraft mentions took " + elapsed + " ns");
    }

    private static long timePattern(Pattern pattern, String line, int repeats) {
        long started = System.nanoTime();
        boolean matched = false;
        for (int i = 0; i < repeats; i++) {
            matched = pattern.matcher(line).find();
        }
        assertTrue(!matched);
        return System.nanoTime() - started;
    }

    private static long timeRegexTimestamps(String[] lines) {
        var pattern = me.wolfii.allthelogs.data.extract.LogTimeExtractor.LINE_START;
        int hits = 0;
        for (int w = 0; w < 2; w++) {
            hits = 0;
            for (String line : lines) {
                if (pattern.matcher(line).find()) hits++;
            }
        }
        long started = System.nanoTime();
        hits = 0;
        for (String line : lines) {
            if (pattern.matcher(line).find()) hits++;
        }
        assertTrue(hits > 0);
        return System.nanoTime() - started;
    }

    private static long timeHandTimestamps(String[] lines) {
        int hits = 0;
        for (int w = 0; w < 2; w++) {
            hits = 0;
            for (String line : lines) {
                if (me.wolfii.allthelogs.data.extract.LogTimeExtractor.match(line) != null) hits++;
            }
        }
        long started = System.nanoTime();
        hits = 0;
        for (String line : lines) {
            if (me.wolfii.allthelogs.data.extract.LogTimeExtractor.match(line) != null) hits++;
        }
        assertTrue(hits > 0);
        return System.nanoTime() - started;
    }

    private static ParsedLog parse(String log) throws IOException {
        return LogParser.parse(new BufferedReader(new StringReader(log), 1 << 16));
    }

    private static String typicalLog(int chatLines) {
        StringBuilder log = new StringBuilder(chatLines * 80);
        log.append("[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3\n");
        log.append("[10:00:01] [Render thread/INFO]: Setting user: JustAlittleWolf\n");
        log.append("[10:00:02] [Render thread/INFO]: Connecting to unicacity.eu, 25565\n");
        for (int i = 0; i < chatLines; i++) {
            log.append("[10:00:10] [Render thread/INFO]: [CHAT] hello there player #").append(i);
            if (i % 17 == 0) {
                log.append(" Connecting to spawn, lobby, and dump, never a host");
            }
            log.append('\n');
        }
        return log.toString();
    }
}
