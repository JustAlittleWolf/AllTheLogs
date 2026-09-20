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
}
