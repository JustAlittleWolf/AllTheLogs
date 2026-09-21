package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand parsers must keep the same captures as the regexes they replaced.
 */
class ExtractorRegexEquivalenceTest {
    private static final Pattern[] VERSION = {
        Pattern.compile("Loading Minecraft (\\S+) with (?:Fabric|Quilt) Loader"),
        Pattern.compile("Loading for game Minecraft (\\S+)"),
        Pattern.compile("for Minecraft (\\S+) loading"),
        Pattern.compile("OptiFine[_ ](\\d+\\.\\d+(?:\\.\\d+)?)"),
        Pattern.compile("(?i)^\\s*-\\s*minecraft\\s+(\\S+)"),
        Pattern.compile("(?:^|[\\s,])minecraft@([^,\\s]+)"),
        Pattern.compile("Minecraft Version: (\\S+)"),
        Pattern.compile("--version,? (\\S+)"),
        Pattern.compile("Starting integrated minecraft server version (.+)\\s*$")
    };
    private static final Pattern SETTING_USER = Pattern.compile("Setting user: (\\S+)");
    private static final Pattern IAS_LOGGING = Pattern.compile(
        "IAS: Logging \\([^)]+\\) as [0-9a-fA-F-]{36}/(\\S+)");
    private static final Pattern IAS_SUCCESS = Pattern.compile(
        "IAS: Successful login as MCProfile\\[uuid=[0-9a-fA-F-]+, name=([^,\\]]+)]");
    private static final Pattern IAS_LOGIN_DATA = Pattern.compile(
        "IAS: Received login request: LoginData\\{name='([^']+)'");
    private static final Pattern CONNECTING = Pattern.compile("Connecting to ([^,]+), (\\d+)\\s*$");
    private static final Pattern SAVING_LEVEL = Pattern.compile(
        "Saving chunks for level '(?:ServerLevel\\[([^]]+)]|([^']+))'");
    private static final Pattern LOADING_DIMENSION = Pattern.compile(
        "Loading dimension -?\\d+ \\(([^)]+)\\) \\(net\\.minecraft\\.server\\.integrated\\.IntegratedServer@");

    @Test
    void versionHandParseMatchesOriginalRegex() {
        for (String line : versionLines()) {
            String regex = firstVersionGroup(line);
            MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
            versions.accept(line);
            assertEquals(regex, versions.version(), line);
            for (int i = 0; i < VERSION.length; i++) {
                String group = regexGroup(VERSION[i], line, 1);
                String hand = MinecraftVersionExtractor.extract(i, line);
                if (hand != null) hand = hand.strip();
                if (hand != null && hand.isEmpty()) hand = null;
                String expected = group == null ? null : group.strip();
                if (expected != null && expected.isEmpty()) expected = null;
                assertEquals(expected, hand, i + ": " + line);
            }
        }
    }

    @Test
    void userHandParseMatchesOriginalRegex() {
        for (String line : userLines()) {
            assertEquals(regexGroup(SETTING_USER, line, 1), MinecraftUserExtractor.settingUser(line), line);
            assertEquals(regexGroup(IAS_LOGGING, line, 1), MinecraftUserExtractor.iasLogging(line), line);
            String success = regexGroup(IAS_SUCCESS, line, 1);
            if (success != null) success = success.strip();
            assertEquals(success, MinecraftUserExtractor.iasSuccess(line), line);
            assertEquals(regexGroup(IAS_LOGIN_DATA, line, 1), MinecraftUserExtractor.iasLoginData(line), line);
        }
    }

    @Test
    void placeHandParseMatchesOriginalRegex() {
        for (String line : placeLines()) {
            Matcher connecting = CONNECTING.matcher(line);
            if (connecting.find()) {
                ServerOrWorldExtractor places = new ServerOrWorldExtractor();
                places.accept(line);
                String host = stripTrailingDots(connecting.group(1).strip());
                int port = Integer.parseInt(connecting.group(2));
                String expected = port > 0 && port != 25565
                    ? ServerOrWorldExtractor.remote(host + ":" + port)
                    : ServerOrWorldExtractor.remote(host);
                assertEquals(expected, places.current(), line);
            }
            Matcher saving = SAVING_LEVEL.matcher(line);
            String savingName = null;
            if (saving.find()) {
                savingName = saving.group(1) != null ? saving.group(1) : saving.group(2);
            }
            assertEquals(savingName, ServerOrWorldExtractor.savingLevelWorld(line), line);
            Matcher loading = LOADING_DIMENSION.matcher(line);
            String loadingName = loading.find() ? loading.group(1) : null;
            assertEquals(loadingName, ServerOrWorldExtractor.loadingDimensionWorld(line), line);
        }
    }

    private static String firstVersionGroup(String line) {
        for (Pattern pattern : VERSION) {
            String group = regexGroup(pattern, line, 1);
            if (group == null) continue;
            group = group.strip();
            if (!group.isEmpty()) return group;
        }
        return null;
    }

    private static String stripTrailingDots(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '.') end--;
        return end == value.length() ? value : value.substring(0, end);
    }

    private static String regexGroup(Pattern pattern, String line, int group) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() ? matcher.group(group) : null;
    }

    private static List<String> versionLines() {
        return List.of(
            "[10:00:00] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3",
            "[10:00:00] [main/INFO]: Loading Minecraft 1.20.1 with Quilt Loader 0.20.0",
            "[10:00:00] [main/INFO]: Loading Minecraft  with Fabric Loader 0.19.3",
            "[23:15:40] [main/INFO]: Loading for game Minecraft 1.16.5",
            "[17:43:34] [main/INFO] [FML]: Forge Mod Loader version 14.23.5.2860 for Minecraft 1.12.2 loading",
            "[18:14:43] [main/INFO]: [OptiFine] OptiFine_1.12.2_HD_U_G6_pre1",
            "OptiFine 1.8.9",
            "OptiFine_1.20",
            "OptiFine_nope",
            "\t- minecraft 1.20.2",
            "  - Minecraft 1.19.4 extra",
            "-minecraft\t1.18",
            "[23:15:50] [main/INFO]: [FabricLoader] Loading 3 mods: minecraft@1.16.5, java@8, fabricloader@0.11.3",
            "foo-minecraft@1.21.4",
            "minecraft@1.21.4,java@8",
            "Minecraft Version: 1.18.2",
            "Minecraft Version:1.18.2",
            "Completely ignored arguments: [--mixin, mixins.feather.json, --versionType, feather]",
            "Completely ignored arguments: [--username, x, --version, 1.20.1]",
            "args: --version 26.2 --bar",
            "[21:20:01] [Server thread/INFO]: Starting integrated minecraft server version 26.1 Snapshot 2",
            "[21:20:01] [Server thread/INFO]: Starting integrated minecraft server version 9.9.9   ",
            "[10:00:03] [main/INFO]: Loading mixin minecraft/client/renderer/chunk0 from fabric-rendering",
            "hello",
            ""
        );
    }

    private static List<String> userLines() {
        return List.of(
            "[11:21:53] [Render thread/INFO]: Setting user: JustAlittleWolf",
            "Setting user: ",
            "prefix Setting user:  next Setting user: Second",
            "[12:00:00] [IAS/INFO]: IAS: Logging (Microsoft) as 8bfd1b6f-0d35-48e4-bacf-bd99a8ec4fe0/JustAlittlePanda",
            "IAS: Logging () as 8bfd1b6f-0d35-48e4-bacf-bd99a8ec4fe0/Nope",
            "[12:00:01] [IAS/INFO]: IAS: Successful login as MCProfile[uuid=8bfd1b6f-0d35-48e4-bacf-bd99a8ec4fe0, name=KeinAcUwU]",
            "IAS: Successful login as MCProfile[uuid=abc, name=  Spaced  ]",
            "[12:00:02] [IAS/INFO]: IAS: Received login request: LoginData{name='JustAlittleWolf', uuid=51709a74-fd73-4c24-a1be-863c0bc4ced7, token=[TOKEN], online=true}",
            "IAS: Logging (Microsoft) as not-a-uuid-at-all-nope-nope-no/Name",
            "Guest[local:E:aaaaaaaa] logged in with entity id 2 at (0, 0, 0)"
        );
    }

    private static List<String> placeLines() {
        return List.of(
            "[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565",
            "[14:45:00] [Render thread/INFO]: Connecting to localhost, 25565",
            "[09:32:50] [Render thread/INFO]: Connecting to mc.gommehd.net., 25565",
            "[24Feb2026 16:08:07.204] [Render thread/INFO]: Connecting to 185.206.150.34, 25588",
            "Connecting to hello world, 1",
            "Connecting tofoo, 25565",
            "Connecting to host, 25565 trailing",
            "Connecting to a, b Connecting to real.example, 25565",
            "[12:00:00] [Render thread/INFO]: [CHAT] Connecting to hello, world, the end",
            "[12:50:39] [Server thread/INFO]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld",
            "[16:40:16] [Server thread/INFO]: Saving chunks for level 'LinkcraftII'/Overworld",
            "Saving chunks for level 'ServerLevel[]'/overworld",
            "[13:05:15] [Server thread/INFO]: Loading dimension 0 (Tick Rate Demonstration) (net.minecraft.server.integrated.IntegratedServer@760f883f)",
            "Loading dimension -1 (Nether) (net.minecraft.server.integrated.IntegratedServer@abc)",
            "Loading dimension foo Loading dimension 0 (World) (net.minecraft.server.integrated.IntegratedServer@1)",
            "Loading dimension 0 (World) (DedicatedServer@1)"
        );
    }
}
