package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.store.SessionMarker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionMarkerTest {
    @Test
    void newIdsAreUniqueUuids() {
        String first = SessionMarker.newId();
        String second = SessionMarker.newId();
        assertTrue(SessionMarker.isId(first));
        assertTrue(SessionMarker.isId(second));
        assertNotEquals(first, second);
    }

    @Test
    void findsTheIdInAMinecraftLogLine() {
        String id = "550e8400-e29b-41d4-a716-446655440000";
        String line = "[10:00:02] [allthelogs-store/INFO]: " + SessionMarker.message(id);
        assertEquals(id, SessionMarker.find(line).orElseThrow());
    }

    @Test
    void ignoresOrdinaryLogLines() {
        assertTrue(SessionMarker.find("[10:00:10] [Render thread/INFO]: [CHAT] hello").isEmpty());
        assertTrue(SessionMarker.find("AllTheLogs session not-a-uuid").isEmpty());
        assertFalse(SessionMarker.isId("session/0"));
    }

    @Test
    void roundTripsThroughTheStoredEntryPath() {
        String id = SessionMarker.newId();
        assertEquals(id, SessionMarker.idFromEntryPath(SessionMarker.entryPath(id)));
        assertNull(SessionMarker.idFromEntryPath("session/0"));
        assertNull(SessionMarker.idFromEntryPath(null));
    }
}
