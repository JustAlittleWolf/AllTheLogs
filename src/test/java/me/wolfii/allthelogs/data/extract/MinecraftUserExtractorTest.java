package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinecraftUserExtractorTest {
    @Test
    void settingUserIsAuthoritativeOverLanGuests() {
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        users.accept("[11:21:53] [Render thread/INFO]: Setting user: JustAlittleWolf");
        users.accept("[12:50:39] [Server thread/INFO]: Guest[local:E:aaaaaaaa] logged in with entity id 2 at (0, 0, 0)");
        assertEquals("JustAlittleWolf", users.user());
    }

    @Test
    void iasLoginReplacesTheLaunchUser() {
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        users.accept("[11:21:53] [Render thread/INFO]: Setting user: JustAlittleWolf");
        users.accept("[12:00:00] [IAS/INFO]: IAS: Logging (Microsoft) as 8bfd1b6f-0d35-48e4-bacf-bd99a8ec4fe0/JustAlittlePanda");
        assertEquals("JustAlittlePanda", users.user());
        users.accept("[12:00:01] [IAS/INFO]: IAS: Successful login as MCProfile[uuid=8bfd1b6f-0d35-48e4-bacf-bd99a8ec4fe0, name=KeinAcUwU]");
        assertEquals("KeinAcUwU", users.user());
        users.accept("[12:00:02] [IAS/INFO]: IAS: Received login request: LoginData{name='JustAlittleWolf', uuid=51709a74-fd73-4c24-a1be-863c0bc4ced7, token=[TOKEN], online=true}");
        assertEquals("JustAlittleWolf", users.user());
    }

    @Test
    void firstLanLoginIsOnlyAFallback() {
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        users.accept("[12:50:39] [Server thread/INFO]: JustAlittleWolf[local:E:67563101] logged in with entity id 1 at (0, 0, 0)");
        users.accept("[12:50:40] [Server thread/INFO]: Guest[local:E:aaaaaaaa] logged in with entity id 2 at (0, 0, 0)");
        assertEquals("JustAlittleWolf", users.user());
    }

    @Test
    void voiceHandshakesFollowObservedAccountChanges() {
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        users.accept("[20:20:00] [Client thread/INFO]: Setting user: JustAlittleWolf");
        users.accept("[20:21:00] [pool-37-thread-1/INFO]: Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear)");
        assertEquals("JustAlittleBear", users.user());
        users.accept("[20:21:25] [pool-46-thread-1/INFO]: Auth VoiceAuth(authMode=MOJANG, token=JustAlittleWolf)");
        assertEquals("JustAlittleWolf", users.user());
        users.accept("[20:27:10] [pool-83-thread-1/INFO]: Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear)");
        assertEquals("JustAlittleBear", users.user());
        users.accept("[22:15:26] [pool-420-thread-1/INFO]: Auth VoiceAuth(authMode=MOJANG, token=JustAlittleM0use)");
        assertEquals("JustAlittleM0use", users.user());
        users.accept("[22:16:00] [Server thread/INFO]: Guest[local:E:aaaaaaaa] logged in with entity id 2 at (0, 0, 0)");
        assertEquals("JustAlittleM0use", users.user());
        users.accept("[22:17:00] [IAS/INFO]: IAS: Received login request: LoginData{name='JustAlittleOtter'}");
        assertEquals("JustAlittleOtter", users.user());
    }

    @Test
    void voiceHandshakeCanIdentifyAnOtherwiseUnknownUser() {
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        users.accept("[16:52:13] [pool-57-thread-1/INFO]: Auth VoiceAuth(authMode=MOJANG, token=Other_Player123)  ");
        assertEquals("Other_Player123", users.user());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Auth VoiceAuth(authMode=LABY, token=JustAlittleBear)",
        "Auth VoiceAuth(authMode=MOJANG, token=)",
        "Auth VoiceAuth(authMode=MOJANG, token=89d7b8ddacd44e2c892f5f3a9ef7d880)",
        "Auth VoiceAuth(authMode=MOJANG, token=ThisNameIsTooLong1)",
        "Auth VoiceAuth(authMode=MOJANG, token=Not-A-Player)",
        "Auth VoiceAuth(authMode=MOJANG, token=Two Players)",
        "Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear",
        "Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear) trailing text",
        "[CHAT] Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear)",
        "Translation for \"Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear)\"",
        "Unexpected message: [pool-57-thread-1/INFO]: Auth VoiceAuth(authMode=MOJANG, token=JustAlittleBear)"
    })
    void unrelatedOrMalformedHandshakesCannotReplaceTheUser(String message) {
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        users.accept("[11:21:53] [Client thread/INFO]: Setting user: Original");
        users.accept("[16:52:13] [pool-57-thread-1/INFO]: " + message);
        assertEquals("Original", users.user());
    }
}
