package me.wolfii.allthelogs.data.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerOrWorldExtractorTest {
    @Test
    void connectingAndLeaveUpdateCurrentAndKeepLast() {
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        places.accept("[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565");
        assertEquals("unicacity.eu", places.current());
        places.accept("[14:44:49] [Render thread/INFO]: Stopping [1] Worker Daemon threads");
        assertNull(places.current());
        assertEquals("unicacity.eu", places.last());
        places.accept("[14:44:50] [Render thread/INFO]: Stopping worker threads");
        assertNull(places.current());
        places.accept("[14:45:00] [Render thread/INFO]: Connecting to localhost, 25565");
        assertEquals("localhost", places.current());
        assertEquals("localhost", places.last());
    }

    @Test
    void isLeaveRecognisesWorkerStopLines() {
        assertTrue(ServerOrWorldExtractor.isLeave(
            "[14:44:49] [Render thread/INFO]: Stopping [1] Worker Daemon threads"));
        assertTrue(ServerOrWorldExtractor.isLeave(
            "[14:44:50] [Render thread/INFO]: Stopping worker threads"));
        assertFalse(ServerOrWorldExtractor.isLeave(
            "[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565"));
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
    }

    @Test
    void isSessionStartRecognisesIntegratedServerAndConnect() {
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[12:50:38] [Server thread/INFO]: Starting integrated minecraft server version 26.3 Snapshot 9"));
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[09:32:50] [Render thread/INFO]: Connecting to unicacity.eu, 25565"));
        assertTrue(ServerOrWorldExtractor.isSessionStart(
            "[12:50:39] [Server thread/INFO]: JustAlittleWolf[local:E:67563101] logged in with entity id 1 at (0, 0, 0)"));
        assertFalse(ServerOrWorldExtractor.isSessionStart(
            "[12:50:40] [Render thread/INFO]: [CHAT] hi"));
    }
}
