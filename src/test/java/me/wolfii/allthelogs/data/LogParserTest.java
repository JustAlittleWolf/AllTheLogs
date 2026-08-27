package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.parse.LogParser;
import me.wolfii.allthelogs.data.parse.ParsedLog;
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

    @Test
    void extractsASessionMarkerWithoutTreatingItAsChat() throws IOException {
        String id = "550e8400-e29b-41d4-a716-446655440000";
        ParsedLog parsed = parse("""
                [10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
                [10:00:02] [allthelogs-store/INFO]: AllTheLogs session %s
                [10:00:10] [Render thread/INFO]: [CHAT] hello
                """.formatted(id));
        assertEquals(id, parsed.sessionId());
        assertEquals(List.of("hello"), parsed.entries().stream().map(ParsedLog.Entry::message).toList());
    }

    @Test
    void detectsForgeVersionWhenTheLoggerNameIsPresent() throws IOException {
        ParsedLog parsed = parse("""
                [17:43:34] [main/INFO] [FML]: Forge Mod Loader version 14.23.5.2860 for Minecraft 1.12.2 loading
                [17:43:40] [Client thread/INFO]: [CHAT] hi
                """);
        assertEquals("1.12.2", parsed.minecraftVersion());
    }

    @Test
    void detectsOptiFineVersion() throws IOException {
        ParsedLog parsed = parse("""
                [18:14:43] [main/INFO]: [OptiFine] OptiFine_1.12.2_HD_U_G6_pre1
                [18:14:50] [Client thread/INFO]: [CHAT] hi
                """);
        assertEquals("1.12.2", parsed.minecraftVersion());
    }

    @Test
    void detectsFabricVersionFromTheModsList() throws IOException {
        ParsedLog parsed = parse("""
                [12:04:36] [main/INFO]: [FabricLoader/]: Loading 4 mods:
                \t- fabricloader 0.14.24
                \t- java 17
                \t- labymod 4.1.3
                \t- minecraft 1.20.2
                [12:04:40] [Render thread/INFO]: [CHAT] hi
                """);
        assertEquals("1.20.2", parsed.minecraftVersion());
    }

    @Test
    void storesTheMinecraftUserFromTheSettingUserLine() throws IOException {
        ParsedLog parsed = parse("""
                [11:21:53] [Render thread/INFO]: Setting user: JustAlittleWolf
                [11:21:54] [Render thread/INFO]: [CHAT] hi
                """);
        assertEquals("JustAlittleWolf", parsed.minecraftUser());
    }

    @Test
    void keepsEmbeddedNewlinesAndStripsFormattingCodes() throws IOException {
        ParsedLog parsed = parse("""
                [19:22:14] [Client thread/INFO] [net.labymod.core_implementation.mc18.gui.GuiChatAdapter]: [CHAT]\s\s

                            KING BENNY IS HOSTING A GIVEAWAY

                           An online player will be chosen in 5m
                              Winner will receive 5 Cubits! Good Luck

                  \u00a77\u2588
                """);
        assertEquals(1, parsed.entries().size());
        String message = parsed.entries().getFirst().message();
        assertTrue(message.contains("KING BENNY IS HOSTING A GIVEAWAY"));
        assertTrue(message.contains("\n"));
        assertTrue(message.contains("\u2588"));
        assertEquals(-1, message.indexOf('\u00a7'));
    }
}
