package me.wolfii.allthelogs.client.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {
    @Test
    void multiplyStacksChannelsAndWhiteIsANoOp() {
        assertEquals(Colors.ESCAPE_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.ESCAPE_TEXT));
        assertEquals(Colors.MATCH_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.MATCH_TEXT));
    }

    @Test
    void brightnessMultiplyTreatsOneHundredAsWhiteAndFiftyAsHalf() {
        assertEquals(0xFFFFFFFF, Colors.brightnessMultiply(100));
        assertEquals(0xFF808080, Colors.brightnessMultiply(50));
        assertEquals(0xFF1A1A1A, Colors.brightnessMultiply(10));
        assertEquals(Colors.brightnessMultiply(10), Colors.brightnessMultiply(0));
        assertEquals(Colors.brightnessMultiply(100), Colors.brightnessMultiply(200));
        assertEquals(Colors.CONTEXT_TEXT, Colors.brightnessMultiply(40));
        assertEquals(Colors.MATCH_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.brightnessMultiply(100)));
    }
}
