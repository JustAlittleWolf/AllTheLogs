package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.parse.MessageCharacters;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared matching for metadata patches and import collision removal.
 * Two chat lines are the same event when their timestamps fall within {@link #WINDOW_SECONDS}
 * and their text matches after a stored {@code \n} pair is treated as a newline. Characters
 * {@link MessageCharacters} removes are ignored, so an older row still matches a newly stored line.
 */
public final class EntryMatch {
    public static final int WINDOW_SECONDS = 3;

    private EntryMatch() {
    }

    /**
     * SQL that treats a stored {@code \n} pair as a live newline and ignores characters
     * {@link MessageCharacters} removes, so an older row still matches a newly stored line.
     */
    public static String normalizedText(String column) {
        String text = "replace(" + column + ", chr(92) || 'n', chr(10))";
        text = "replace(" + text + ", chr(" + MessageCharacters.OBJECT_REPLACEMENT_CODE_POINT + "), '')";
        if (MessageCharacters.dropPrivateUseCharacters()) {
            text = "regexp_replace(" + text + ", '\\p{Co}', '', 'g')";
        }
        return text;
    }

    /**
     * Equality of two message columns after newline normalisation.
     */
    public static String sameText(String left, String right) {
        return normalizedText(left) + " = " + normalizedText(right);
    }

    /**
     * {@code left} is already newline-normalised; {@code right} still needs it.
     */
    public static String sameAsNormalized(String rawColumn, String normalizedColumn) {
        return normalizedText(rawColumn) + " = " + normalizedColumn;
    }

    public static String withinWindow(String leftTime, String rightTime) {
        return "abs(date_diff('millisecond', " + leftTime + ", " + rightTime + ")) <= "
            + (WINDOW_SECONDS * 1000);
    }

    /**
     * Materialises {@code sourceSql} as {@code normTable} and expands each row across
     * {@code ±}{@link #WINDOW_SECONDS} as {@code secondsTable.join_second} so a later
     * {@code date_trunc('second', other.entry_time) = join_second} can use an equality join.
     * {@code sourceSql} must expose {@code entry_time} and {@code entry_second}.
     */
    public static void expandSeconds(Statement statement, String sourceSql, String normTable, String secondsTable)
        throws SQLException {
        statement.execute("CREATE OR REPLACE TEMP TABLE " + normTable + " AS " + sourceSql);
        statement.execute("""
            CREATE OR REPLACE TEMP TABLE %s AS
            SELECT p.*, p.entry_second + (delta * INTERVAL 1 SECOND) AS join_second
            FROM %s p
            CROSS JOIN range(%d, %d) t(delta)
            """.formatted(secondsTable, normTable, -WINDOW_SECONDS, WINDOW_SECONDS + 1));
    }
}
