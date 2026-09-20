package me.wolfii.allthelogs.client.script;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Turns the AllTheLogs TypeScript sketch (and ordinary JS-compatible TypeScript) into JavaScript
 * GraalJS can run, without shipping {@code tsc}.
 */
public final class ScriptSource {
    private static final Pattern FROM_IMPORT = Pattern.compile(
        "(?m)^\\s*from\\s+[\"']allthelogs[\"']\\s+import\\s+[^;\\n]+;\\s*");
    private static final Pattern ES_IMPORT = Pattern.compile(
        "(?m)^\\s*import\\s*(?:type\\s+)?(?:\\{[^}]*}|\\*\\s+as\\s+\\w+|\\w+)\\s*from\\s*[\"']allthelogs[\"']\\s*;?\\s*");
    private static final Pattern TYPED_ARROW = Pattern.compile(
        "([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*[A-Za-z_][A-Za-z0-9_.]*\\s*->");
    private static final Pattern PARAM_TYPE = Pattern.compile(
        "([,(]\\s*[A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*[A-Za-z_][A-Za-z0-9_.\\[\\]|\\s<>,]*?(?=\\s*[,)=])");
    private static final Pattern AS_CAST = Pattern.compile(
        "\\s+as\\s+[A-Za-z_][A-Za-z0-9_.\\[\\]]*");
    private static final Pattern RETURN_TYPE = Pattern.compile(
        "\\)\\s*:\\s*[A-Za-z_][A-Za-z0-9_.\\[\\]|<>]*\\s*\\{");

    private ScriptSource() {
    }

    public static String toJavaScript(String source) {
        Objects.requireNonNull(source, "source");
        String js = FROM_IMPORT.matcher(source).replaceAll("");
        js = ES_IMPORT.matcher(js).replaceAll("");
        js = TYPED_ARROW.matcher(js).replaceAll("($1) =>");
        js = PARAM_TYPE.matcher(js).replaceAll("$1");
        js = AS_CAST.matcher(js).replaceAll("");
        js = RETURN_TYPE.matcher(js).replaceAll(") {");
        return js;
    }
}
