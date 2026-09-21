package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerOrWorldExtractorTest {
    @Test
    void connectingAndLeaveUpdateCurrentAndKeepLast() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565");
        assertEquals("unicacity.eu", places.current());
        places.accept("[14:44:49] [Render thread/WARN]: Client disconnected with reason: Disconnected");
        assertNull(places.current());
        assertEquals("unicacity.eu", places.last());
        places.accept("[14:44:50] [Render thread/INFO]: Stopping worker threads");
        assertNull(places.current());
        places.accept("[14:45:00] [Render thread/INFO]: Connecting to localhost, 25565");
        assertEquals("localhost", places.current());
        assertEquals("localhost", places.last());
    }

    @Test
    void workerThreadStopsAreNotLeaves() {
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[14:44:49] [Render thread/INFO]: Stopping [1] Worker Daemon threads"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[14:44:50] [Render thread/INFO]: Stopping worker threads"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[12:50:40] [Server thread/INFO]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld"));
    }

    @Test
    void isLeaveRecognisesSingleplayerShutdown() {
        assertTrue(ServerOrWorldExtractor.isLeave(
            "[12:50:41] [Server thread/INFO]: Stopping singleplayer server as player logged out"));
        assertTrue(ServerOrWorldExtractor.isLeave(
            "[12:50:41] [Render thread/INFO]: Stopping!"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[12:50:40] [Render thread/INFO]: [CHAT] Stopping!"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[12:50:40] [Server thread/INFO]: Saving and pausing game..."));
        assertTrue(ServerOrWorldExtractor.isLeave(
            "[15:46:00] [Render thread/WARN]: Client disconnected with reason: Disconnected"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[15:46:00] [Render thread/ERROR]: Can't ping hypixel.net: Disconnected"));
    }

    @Test
    void dropsTrailingDotsOnFqdnConnectsAndIgnoresSavesAfterLeave() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[14:44:40] [Render thread/INFO]: Connecting to mc.gommehd.net., 25565");
        assertEquals("mc.gommehd.net", places.current());
        places.accept("[14:44:49] [Render thread/WARN]: Client disconnected with reason: Connection reset");
        assertNull(places.current());
        places.accept("[14:44:50] [Server thread/INFO]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld");
        assertNull(places.current());
        assertEquals("mc.gommehd.net", places.last());
        places.accept("[14:45:00] [Server thread/INFO]: Starting integrated minecraft server version 26.2");
        places.accept("[14:45:01] [Server thread/INFO]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld");
        assertEquals("world/New World", places.current());
    }

    @Test
    void isSessionStartRecognisesIntegratedServerAndConnect() {
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[12:50:38] [Server thread/INFO]: Starting integrated minecraft server version 26.3 Snapshot 9"));
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[09:32:50] [Render thread/INFO]: Connecting to unicacity.eu, 25565"));
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[12:50:39] [Server thread/INFO]: JustAlittleWolf[local:E:67563101] logged in with entity id 1 at (0, 0, 0)"));
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[12:50:38] [Server thread/INFO]: Generating keypair"));
        assertTrue(ServerOrWorldExtractor.isSingleplayerJoin(
            "[12:50:38] [Server thread/INFO]: Starting integrated minecraft server version 26.2"));
        assertFalse(ServerOrWorldExtractor.isSingleplayerJoin(
            "[09:32:50] [Render thread/INFO]: Connecting to unicacity.eu, 25565"));
        assertFalse(ServerOrWorldExtractor.isSessionStart(
            "[12:50:40] [Render thread/INFO]: [CHAT] hi"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[16:00:37] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric-api"));
    }

    @Test
    void resourceReloadsDoNotLeaveWhileConnected() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[16:00:32] [Render thread/INFO]: Connecting to unicacity.eu, 25565");
        places.accept("[16:00:37] [Render thread/INFO]: Reloading ResourceManager: vanilla, server/00000000/pack");
        assertEquals("unicacity.eu", places.current());
        places.accept("[16:10:00] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric-api");
        assertEquals("unicacity.eu", places.current());
        assertTrue(places.inSession());
    }

    @Test
    void startingIntegratedServerDropsARemotePlaceBeforeTheWorldNameIsKnown() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[09:32:50] [Render thread/INFO]: Connecting to unicacity.eu, 25565");
        places.accept("[12:49:12] [Render thread/INFO]: Reloading ResourceManager: vanilla, fabric-api");
        places.accept("[12:49:19] [Server thread/INFO]: Starting integrated minecraft server version 26.2");
        assertNull(places.current());
        assertTrue(places.inSession());
        places.accept("[12:49:19] [Server thread/INFO]: Generating keypair");
        assertNull(places.current());
        places.accept("[12:49:20] [Server thread/INFO]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld");
        assertEquals("world/New World", places.current());
    }

    @Test
    void worldSavesDoNotOverrideARemoteServer() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565");
        places.accept("[14:44:50] [Server thread/INFO]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld");
        assertEquals("unicacity.eu", places.current());
    }

    @Test
    void loadingDimensionAfterRemoteSetsTheWorldImmediately() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[13:11:02] [Client thread/INFO]: Connecting to unicacity.eu, 25565");
        places.accept("[13:05:15] [Server thread/INFO]: Loading dimension 0 (Tick Rate Demonstration) (net.minecraft.server.integrated.IntegratedServer@760f883f)");
        assertEquals("world/Tick Rate Demonstration", places.current());
    }

    @Test
    void connectingPatternDoesNotBacktrackOnCommaFilledChat() {
        String chat = "[12:00:00] [Render thread/INFO]: [CHAT] Connecting to "
            + "hello, world, ".repeat(500) + "the end";
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept(chat);
        assertNull(places.current());
        assertFalse(ServerOrWorldExtractor.isSessionStart(chat));
    }
}
