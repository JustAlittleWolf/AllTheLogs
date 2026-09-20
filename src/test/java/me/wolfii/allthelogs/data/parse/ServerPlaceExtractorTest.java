package me.wolfii.allthelogs.data.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerPlaceExtractorTest {
    @Test
    void connectingAndLeaveUpdateCurrentAndKeepLastPlace() {
        ServerPlaceExtractor places = new ServerPlaceExtractor();
        places.accept("[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565");
        assertEquals("unicacity.eu", places.current());
        places.accept("[14:44:49] [Render thread/INFO]: Stopping [1] Worker Daemon threads");
        assertNull(places.current());
        assertEquals("unicacity.eu", places.place());
        places.accept("[14:44:50] [Render thread/INFO]: Stopping worker threads");
        assertNull(places.current());
        places.accept("[14:45:00] [Render thread/INFO]: Connecting to localhost, 25565");
        assertEquals("localhost", places.current());
        assertEquals("localhost", places.place());
    }

    @Test
    void isLeaveRecognisesWorkerStopLines() {
        assertTrue(ServerPlaceExtractor.isLeave(
            "[14:44:49] [Render thread/INFO]: Stopping [1] Worker Daemon threads"));
        assertTrue(ServerPlaceExtractor.isLeave(
            "[14:44:50] [Render thread/INFO]: Stopping worker threads"));
        assertFalse(ServerPlaceExtractor.isLeave(
            "[14:44:40] [Render thread/INFO]: Connecting to unicacity.eu, 25565"));
    }
}
