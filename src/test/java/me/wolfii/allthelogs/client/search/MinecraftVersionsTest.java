package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftVersionsTest {
    @Test
    void unknownIsFirstAndThenTheNewestSemanticVersion() {
        assertEquals(List.of("unknown", "26.2", "1.21.1", "1.20.1", "1.12.2", "1.8.9"),
            MinecraftVersions.newestFirst(List.of("1.8.9", "26.2", "1.12.2", "unknown", "1.21.1", "1.20.1")));
    }

    @Test
    void preReleaseIsOlderThanTheSameNumbersWithoutASuffix() {
        assertEquals(List.of("1.21.0", "1.21.0-rc1", "1.21.0-pre2"),
            MinecraftVersions.newestFirst(List.of("1.21.0-pre2", "1.21.0", "1.21.0-rc1")));
    }

    @Test
    void majorKeepsTheFirstTwoComponents() {
        assertEquals("1.8", MinecraftVersions.major("1.8.9"));
        assertEquals("1.8", MinecraftVersions.major("1.8"));
        assertEquals("1.21", MinecraftVersions.major("1.21.4"));
        assertEquals("1.21", MinecraftVersions.major("1.21.4-rc1"));
        assertEquals("1.21", MinecraftVersions.major("1.21-rc1"));
        assertEquals("26.2", MinecraftVersions.major("26.2"));
        assertEquals("26.2", MinecraftVersions.major("26.2.1"));
        assertEquals("24w14a", MinecraftVersions.major("24w14a"));
        assertEquals("unknown", MinecraftVersions.major("unknown"));
        assertEquals(null, MinecraftVersions.major(null));
    }
}
