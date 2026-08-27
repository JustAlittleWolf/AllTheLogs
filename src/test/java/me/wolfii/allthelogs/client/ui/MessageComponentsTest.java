package me.wolfii.allthelogs.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageComponentsTest {
    @Test
    void matchCountTextCapsAt99() {
        assertEquals("0", MessageComponents.matchCountText(0));
        assertEquals("12", MessageComponents.matchCountText(12));
        assertEquals(">99", MessageComponents.matchCountText(100));
    }
}
