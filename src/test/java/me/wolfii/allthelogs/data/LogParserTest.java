package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.internal.LogParser;
import me.wolfii.allthelogs.data.internal.ParsedLog;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {
    private static ParsedLog parse(String log) throws IOException {
        return LogParser.parse(new BufferedReader(new StringReader(log)));
    }

    @Test
    void keepsOnlyChatLinesAndStripsTheMarker() throws IOException {
        ParsedLog parsed = parse("""
                [12:16:21] [Client thread/INFO]: [CHAT] hello
                [12:16:22] [Chunk Batcher 12/WARN]: Needed to grow BufferBuilder buffer
                [12:16:22] [Client thread/INFO]: Dimension initialized -1/Nether
                [12:16:23] [Client thread/INFO]: [CHAT] \u00a7r\u00a7a[Click Here\u00a7a] \u00a77to watch an ad
                """);
        assertEquals(List.of("hello", "[Click Here] to watch an ad"),
                parsed.entries().stream().map(ParsedLog.Entry::message).toList());
        assertEquals(LocalTime.of(12, 16, 21), parsed.entries().getFirst().time());
        assertEquals(LocalTime.of(12, 16, 21), parsed.firstLineTime());
        assertEquals(LocalTime.of(12, 16, 23), parsed.lastLineTime());
    }

    @Test
    void keepsEmptyAndWhitespaceOnlyChatLines() throws IOException {
        ParsedLog parsed = parse("""
                [12:16:21] [Client thread/INFO]: [CHAT]\s
                [12:16:22] [Client thread/INFO]: [CHAT]     \s
                [12:16:23] [Client thread/INFO]: [CHAT] ---- WATCH /AD ----
                """);
        assertEquals(3, parsed.entries().size());
        assertEquals("", parsed.entries().get(0).message());
        assertTrue(parsed.entries().get(1).message().isBlank());
    }

    @Test
    void treatsUnprefixedLinesAsContinuationOfTheChatMessage() throws IOException {
        ParsedLog parsed = parse("""
                [12:16:21] [Render thread/INFO]: [CHAT] <Alan> Aquatic monsters of the deep
                have swarmed the facility!
                [12:16:22] [Render thread/INFO]: [CHAT] next
                """);
        assertEquals(2, parsed.entries().size());
        assertEquals("<Alan> Aquatic monsters of the deep\nhave swarmed the facility!",
                parsed.entries().getFirst().message());
    }

    @Test
    void doesNotAttachContinuationLinesToNonChatLines() throws IOException {
        ParsedLog parsed = parse("""
                [13:22:17] [main/WARN]: Warnings were found!
                 - Mod 'Debugify' recommends yet-another-config-lib, which is missing!
                [13:22:20] [Render thread/INFO]: [CHAT] hi
                """);
        assertEquals(List.of("hi"), parsed.entries().stream().map(ParsedLog.Entry::message).toList());
    }

    @Test
    void detectsFabricVersionFromTheFirstLine() throws IOException {
        assertEquals("26.2", parse(LogFixtures.modernLog("26.2", "hi")).minecraftVersion());
    }

    @Test
    void detectsForgeVersionFromTheModLoaderLine() throws IOException {
        assertEquals("1.8.9", parse(LogFixtures.legacyLog("hi")).minecraftVersion());
    }

    @Test
    void detectsVersionFromTheIntegratedServerWhenNoLoaderLineExists() throws IOException {
        ParsedLog parsed = parse("""
                [18:26:25] [Client thread/INFO]: Setting user: JustAlittleWolf
                [18:26:36] [Server thread/INFO]: Starting integrated minecraft server version 1.8.9
                [18:26:40] [Client thread/INFO]: [CHAT] hi
                """);
        assertEquals("1.8.9", parsed.minecraftVersion());
    }

    @Test
    void prefersTheMostTrustworthyVersionLine() throws IOException {
        ParsedLog parsed = parse("""
                [10:00:00] [main/INFO]: Loading Minecraft 1.20.1 with Fabric Loader 0.17.3
                [10:00:05] [Server thread/INFO]: Starting integrated minecraft server version 9.9.9
                [10:00:06] [Render thread/INFO]: [CHAT] hi
                """);
        assertEquals("1.20.1", parsed.minecraftVersion());
    }

    @Test
    void fallsBackToUnknownVersion() throws IOException {
        ParsedLog parsed = parse("[10:00:00] [Render thread/INFO]: [CHAT] hi\n");
        assertEquals(ChatLog.UNKNOWN_VERSION, parsed.minecraftVersion());
    }

    @Test
    void acceptsTimestampsThatIncludeADate() throws IOException {
        ParsedLog parsed = parse("[2026-08-25 21:04:09] [Render thread/INFO]: [CHAT] hi\n");
        assertEquals(1, parsed.entries().size());
        assertEquals(LocalTime.of(21, 4, 9), parsed.entries().getFirst().time());
    }
}
