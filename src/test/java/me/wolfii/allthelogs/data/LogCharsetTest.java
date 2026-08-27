package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.parse.LogCharset;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogCharsetTest {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Test
    void prefersUtf8WhenTheFileIsValidUnicode() {
        String text = "[10:00:00] [Render thread/INFO]: [CHAT] привет §ahello";
        assertEquals(text, LogCharset.decode(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void keepsUtf8SequencesWhenAWindowsSectionSignIsMixedIn() {
        byte[] cyrillic = "привет".getBytes(StandardCharsets.UTF_8);
        byte[] prefix = "[CHAT] ".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[prefix.length + 1 + cyrillic.length];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        bytes[prefix.length] = (byte) 0xA7;
        System.arraycopy(cyrillic, 0, bytes, prefix.length + 1, cyrillic.length);

        String decoded = LogCharset.decode(bytes);
        assertTrue(decoded.startsWith("[CHAT] "));
        assertEquals('\u00a7', decoded.charAt("[CHAT] ".length()));
        assertTrue(decoded.endsWith("привет"), decoded);
    }

    @Test
    void readsLegacyWindows1252Logs() {
        String text = "[12:00:00] [Client thread/INFO]: [CHAT] \u00a7aHello \u00e4";
        assertEquals(text, LogCharset.decode(text.getBytes(WINDOWS_1252)));
    }

    @Test
    void stripsAUtf8Bom() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[3 + body.length];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        assertEquals("hello", LogCharset.decode(bytes));
    }

    @Test
    void readsUtf16LeWithBom() {
        byte[] bytes = "Setting user: Wolf".getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[2 + bytes.length];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(bytes, 0, withBom, 2, bytes.length);
        assertEquals("Setting user: Wolf", LogCharset.decode(withBom));
    }
}
