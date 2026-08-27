package me.wolfii.allthelogs.data.parse;

import org.mozilla.universalchardet.UniversalDetector;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodes Minecraft log bytes, preferring Unicode.
 * <p>
 * A whole-file Windows-1252 fallback mangles any real UTF-8 sequences as soon as one illegal
 * byte appears (a stray section-sign, a truncated character). Valid UTF-8 is used as-is; otherwise
 * well-formed UTF-8 sequences are kept and only illegal bytes are read as Windows-1252.
 * UTF-16 is recognised from a BOM or {@link UniversalDetector}.
 */
public final class LogCharset {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final byte UTF8_BOM_0 = (byte) 0xEF;
    private static final byte UTF8_BOM_1 = (byte) 0xBB;
    private static final byte UTF8_BOM_2 = (byte) 0xBF;

    private LogCharset() {
    }

    public static String decode(byte[] bytes) {
        if (bytes.length == 0) return "";
        if (hasUtf8Bom(bytes)) {
            return decodeUtf8PreferringUnicode(bytes, 3);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (isStrictUtf8(bytes)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        Charset detected = detect(bytes);
        if (detected != null && isUtf16(detected)) {
            return new String(bytes, detected);
        }
        return decodeUtf8PreferringUnicode(bytes, 0);
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
            && bytes[0] == UTF8_BOM_0
            && bytes[1] == UTF8_BOM_1
            && bytes[2] == UTF8_BOM_2;
    }

    private static boolean isUtf16(Charset charset) {
        String name = charset.name();
        return name.equalsIgnoreCase("UTF-16")
            || name.equalsIgnoreCase("UTF-16LE")
            || name.equalsIgnoreCase("UTF-16BE");
    }

    private static boolean isStrictUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /**
     * Keeps every well-formed UTF-8 character; each illegal byte is one Windows-1252 character.
     */
    static String decodeUtf8PreferringUnicode(byte[] bytes, int offset) {
        StringBuilder text = new StringBuilder(bytes.length - offset);
        int i = offset;
        while (i < bytes.length) {
            int length = utf8SequenceLength(bytes, i);
            if (length > 0) {
                text.append(new String(bytes, i, length, StandardCharsets.UTF_8));
                i += length;
            } else {
                text.append(new String(bytes, i, 1, WINDOWS_1252));
                i++;
            }
        }
        return text.toString();
    }

    /**
     * @return byte length of the UTF-8 sequence at {@code index}, or {@code 0} if it is not valid UTF-8
     */
    static int utf8SequenceLength(byte[] bytes, int index) {
        int lead = bytes[index] & 0xFF;
        if (lead < 0x80) return 1;
        int expected;
        int minCodePoint;
        if (lead >= 0xC2 && lead <= 0xDF) {
            expected = 2;
            minCodePoint = 0x80;
        } else if (lead >= 0xE0 && lead <= 0xEF) {
            expected = 3;
            minCodePoint = lead == 0xE0 ? 0x800 : 0x800;
        } else if (lead >= 0xF0 && lead <= 0xF4) {
            expected = 4;
            minCodePoint = 0x10000;
        } else {
            return 0;
        }
        if (index + expected > bytes.length) return 0;
        for (int i = 1; i < expected; i++) {
            if ((bytes[index + i] & 0xC0) != 0x80) return 0;
        }
        int codePoint = decodeUtf8CodePoint(bytes, index, expected);
        if (codePoint < minCodePoint) return 0;
        if (lead == 0xE0 && codePoint < 0x800) return 0;
        if (lead == 0xED && codePoint >= 0xD800 && codePoint <= 0xDFFF) return 0;
        if (lead == 0xF0 && codePoint < 0x10000) return 0;
        if (lead == 0xF4 && codePoint > 0x10FFFF) return 0;
        if (codePoint > 0x10FFFF) return 0;
        return expected;
    }

    private static int decodeUtf8CodePoint(byte[] bytes, int index, int length) {
        return switch (length) {
            case 1 -> bytes[index] & 0xFF;
            case 2 -> ((bytes[index] & 0x1F) << 6) | (bytes[index + 1] & 0x3F);
            case 3 -> ((bytes[index] & 0x0F) << 12)
                | ((bytes[index + 1] & 0x3F) << 6)
                | (bytes[index + 2] & 0x3F);
            default -> ((bytes[index] & 0x07) << 18)
                | ((bytes[index + 1] & 0x3F) << 12)
                | ((bytes[index + 2] & 0x3F) << 6)
                | (bytes[index + 3] & 0x3F);
        };
    }

    private static Charset detect(byte[] bytes) {
        UniversalDetector detector = new UniversalDetector();
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        String name = detector.getDetectedCharset();
        detector.reset();
        if (name == null) return null;
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
