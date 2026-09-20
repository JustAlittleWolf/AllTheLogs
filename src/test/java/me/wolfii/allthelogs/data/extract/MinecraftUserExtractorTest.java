package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;

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
}
