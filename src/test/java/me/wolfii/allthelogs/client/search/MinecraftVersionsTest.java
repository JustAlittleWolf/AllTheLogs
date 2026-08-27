package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftVersionsTest {
    @Test
    void newestSemanticVersionIsFirstAndUnknownIsLast() {
        assertEquals(List.of("26.2", "1.21.1", "1.20.1", "1.12.2", "1.8.9", "unknown"),
            MinecraftVersions.newestFirst(List.of("1.8.9", "26.2", "1.12.2", "unknown", "1.21.1", "1.20.1")));
    }

    @Test
    void preReleaseIsOlderThanTheSameNumbersWithoutASuffix() {
        assertEquals(List.of("1.21.0", "1.21.0-rc1", "1.21.0-pre2"),
            MinecraftVersions.newestFirst(List.of("1.21.0-pre2", "1.21.0", "1.21.0-rc1")));
    }
}
