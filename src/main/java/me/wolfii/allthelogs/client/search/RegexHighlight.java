package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Cheap regex syntax colouring for the search bar: escapes, classes, groups, quantifiers, and anchors.
 */
public final class RegexHighlight {
    private RegexHighlight() {
    }

    public static FormattedCharSequence sequence(String pattern) {
        return highlight(pattern).getVisualOrderText();
    }

    public static Component highlight(String pattern) {
        if (pattern == null || pattern.isEmpty()) return Component.empty();
        MutableComponent result = Component.empty();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                result.append(colored(pattern.substring(i, i + 2), Colors.REGEX_ESCAPE));
                i += 2;
                continue;
            }
            if (c == '[') {
                int end = classEnd(pattern, i);
                result.append(colored(pattern.substring(i, end), Colors.REGEX_CLASS));
                i = end;
                continue;
            }
            if (c == '{' && countedQuantifierEnd(pattern, i) > i) {
                int end = countedQuantifierEnd(pattern, i);
                result.append(colored(pattern.substring(i, end), Colors.REGEX_QUANTIFIER));
                i = end;
                continue;
            }
            int color = colorOf(c);
            int run = i + 1;
            while (run < pattern.length() && pattern.charAt(run) != '\\' && pattern.charAt(run) != '['
                && pattern.charAt(run) != '{' && colorOf(pattern.charAt(run)) == color) {
                run++;
            }
            result.append(colored(pattern.substring(i, run), color));
            i = run;
        }
        return result.getSiblings().size() == 1 ? result.getSiblings().getFirst() : result;
    }

    static int colorOf(char c) {
        return switch (c) {
            case '(', ')', '|' -> Colors.REGEX_GROUP;
            case '*', '+', '?', '{' -> Colors.REGEX_QUANTIFIER;
            case '.', '^', '$' -> Colors.REGEX_ANCHOR;
            default -> Colors.SEARCH_TEXT;
        };
    }

    private static int classEnd(String pattern, int start) {
        int i = start + 1;
        if (i < pattern.length() && pattern.charAt(i) == '^') i++;
        if (i < pattern.length() && pattern.charAt(i) == ']') i++;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == ']') return i + 1;
            i++;
        }
        return pattern.length();
    }

    private static int countedQuantifierEnd(String pattern, int start) {
        int i = start + 1;
        if (i >= pattern.length() || !Character.isDigit(pattern.charAt(i))) return start;
        while (i < pattern.length() && Character.isDigit(pattern.charAt(i))) i++;
        if (i < pattern.length() && pattern.charAt(i) == ',') {
            i++;
            while (i < pattern.length() && Character.isDigit(pattern.charAt(i))) i++;
        }
        if (i < pattern.length() && pattern.charAt(i) == '}') return i + 1;
        return start;
    }

    private static Component colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }
}
