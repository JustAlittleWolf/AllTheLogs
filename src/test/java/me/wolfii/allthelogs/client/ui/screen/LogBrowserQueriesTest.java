package me.wolfii.allthelogs.client.ui.screen;

import me.wolfii.allthelogs.data.ChatQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogBrowserQueriesTest {
    @Test
    void exclusiveOffsetIncludesTheTargetTimestamp() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 10, 0, 0);
        assertEquals(time.plusNanos(1), LogBrowserQueries.exclusiveOffset(time, ChatQuery.Sort.DESCENDING));
        assertEquals(time.minusNanos(1), LogBrowserQueries.exclusiveOffset(time, ChatQuery.Sort.ASCENDING));
    }
}
