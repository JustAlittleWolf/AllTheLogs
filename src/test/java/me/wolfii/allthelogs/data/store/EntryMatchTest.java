package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.parse.MessageCharacters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryMatchTest {
    @AfterEach
    void restorePrivateUseFilter() {
        MessageCharacters.setDropPrivateUseCharacters(true);
    }
    @Test
    void windowIsThreeSeconds() {
        assertEquals(3, EntryMatch.WINDOW_SECONDS);
        assertTrue(EntryMatch.withinWindow("a", "b").contains("3000"));
    }

    @Test
    void sameTextNormalisesBackslashNOnBothSides() {
        String sql = EntryMatch.sameText("left.message", "right.message");
        assertTrue(sql.contains("chr(92) || 'n'"));
        assertTrue(sql.contains("left.message"));
        assertTrue(sql.contains("right.message"));
    }

    @Test
    void sameAsNormalizedRewritesOnlyTheRawColumn() {
        String sql = EntryMatch.sameAsNormalized("e.message", "p.text");
        assertTrue(sql.contains("replace(e.message, chr(92) || 'n', chr(10))"));
        assertTrue(sql.contains("chr(" + MessageCharacters.OBJECT_REPLACEMENT_CODE_POINT + ")"));
        assertTrue(sql.contains("\\p{Co}"));
        assertTrue(sql.endsWith("= p.text"));
        assertFalse(sql.contains("p.text,"));
    }

    @Test
    void sameAsNormalizedKeepsPrivateUseCharactersWhenThatFilterIsOff() {
        MessageCharacters.setDropPrivateUseCharacters(false);
        String sql = EntryMatch.sameAsNormalized("e.message", "p.text");
        assertTrue(sql.contains("chr(" + MessageCharacters.OBJECT_REPLACEMENT_CODE_POINT + ")"));
        assertFalse(sql.contains("\\p{Co}"));
    }

    @Test
    void normalizedTextDropsPlaceholdersInDuckDb() throws Exception {
        String face = "a" + MessageCharacters.OBJECT_REPLACEMENT + "b";
        String icon = face + "\uE000" + new String(Character.toChars(0xF0000)) + "c";
        try (var connection = StoreConnections.openInMemory();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample (message VARCHAR)");
            statement.execute("INSERT INTO sample VALUES ('" + icon + "')");
            try (ResultSet result = statement.executeQuery(
                "SELECT " + EntryMatch.normalizedText("message") + " FROM sample")) {
                assertTrue(result.next());
                assertEquals("abc", result.getString(1));
            }

            MessageCharacters.setDropPrivateUseCharacters(false);
            try (ResultSet result = statement.executeQuery(
                "SELECT " + EntryMatch.normalizedText("message") + " FROM sample")) {
                assertTrue(result.next());
                assertEquals("ab\uE000" + new String(Character.toChars(0xF0000)) + "c", result.getString(1));
            }
        }
    }
}
