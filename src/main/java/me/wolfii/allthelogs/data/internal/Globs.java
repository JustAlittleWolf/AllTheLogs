package me.wolfii.allthelogs.data.internal;

import java.util.regex.Pattern;

/// Translates the glob syntax used by [me.wolfii.allthelogs.data.ImportOptions#pathMatcher()] into a regex.
///
/// The default file system's path matcher is deliberately not used: paths inside archives are virtual and always use
/// `/`, so matching must behave identically on every operating system.
public final class Globs {
    private Globs() {
    }

    /// Compiles a glob into a pattern that matches whole `/` separated paths.
    ///
    /// Supported: `?` for one character except `/`, `*` for any run of characters except `/`, `**` for any run
    /// including `/`, `[abc]` and `[!abc]` character classes, and `{a,b}` alternatives.
    public static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder("^");
        int depth = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '\\' -> {
                    if (i + 1 >= glob.length()) throw new IllegalArgumentException("glob ends with a dangling escape: " + glob);
                    regex.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
                }
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        // "**" spans any number of directories, including none, so the separator that joins it to its
                        // neighbour is part of the wildcard rather than a required character.
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++;
                            regex.append("(?:.*/)?");
                        } else if (regex.length() > 0 && regex.charAt(regex.length() - 1) == '/') {
                            regex.setLength(regex.length() - 1);
                            regex.append("(?:/.*)?");
                        } else {
                            regex.append(".*");
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '[' -> i = appendCharacterClass(glob, i, regex);
                case '{' -> {
                    depth++;
                    regex.append("(?:");
                }
                case '}' -> {
                    if (depth == 0) throw new IllegalArgumentException("unmatched '}' in glob: " + glob);
                    depth--;
                    regex.append(')');
                }
                case ',' -> regex.append(depth > 0 ? "|" : ",");
                default -> {
                    if (".()+|^$@%".indexOf(c) >= 0) regex.append('\\');
                    regex.append(c);
                }
            }
        }
        if (depth != 0) throw new IllegalArgumentException("unmatched '{' in glob: " + glob);
        return Pattern.compile(regex.append('$').toString());
    }

    private static int appendCharacterClass(String glob, int start, StringBuilder regex) {
        int i = start + 1;
        regex.append('[');
        if (i < glob.length() && (glob.charAt(i) == '!' || glob.charAt(i) == '^')) {
            regex.append('^');
            i++;
        }
        boolean closed = false;
        for (; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == ']') {
                closed = true;
                break;
            }
            if (c == '\\' || c == '[' || c == '&') regex.append('\\');
            regex.append(c);
        }
        if (!closed) throw new IllegalArgumentException("unmatched '[' in glob: " + glob);
        regex.append(']');
        return i;
    }
}
