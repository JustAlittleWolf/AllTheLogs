package me.wolfii.allthelogs.client.script;

import me.wolfii.allthelogs.api.LogDatabase;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Runs a {@code .js} or {@code .ts} file as JavaScript against the public AllTheLogs API.
 * TypeScript is not compiled: {@code example.ts} is JavaScript with a JSDoc TypeScript API sketch.
 */
public final class ScriptHost {
    private ScriptHost() {
    }

    public static Result execute(String source, String fileName, LogDatabase database, Path outputFile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(outputFile, "outputFile");
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        PrintStream print = new PrintStream(console, true, StandardCharsets.UTF_8);
        ScriptOutput output = new ScriptOutput(outputFile);
        try (output; Context context = context(print)) {
            ScriptBindings.install(context.getBindings("js"), database, output);
            context.eval("js", source);
            return Result.ok(console.toString(StandardCharsets.UTF_8), output.written() ? outputFile : null);
        } catch (PolyglotException e) {
            print.println(e.getMessage());
            return Result.failed(console.toString(StandardCharsets.UTF_8), output.written() ? outputFile : null,
                e.getMessage());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            print.println(message);
            return Result.failed(console.toString(StandardCharsets.UTF_8), output.written() ? outputFile : null, message);
        }
    }

    private static Context context(PrintStream print) {
        HostAccess access = HostAccess.newBuilder()
            .allowPublicAccess(true)
            .allowAllImplementations(true)
            .allowArrayAccess(true)
            .allowListAccess(true)
            .allowIterableAccess(true)
            .allowMapAccess(true)
            .allowAccessInheritance(true)
            .build();
        Context.Builder builder = Context.newBuilder("js")
            .allowHostAccess(access)
            .allowHostClassLookup(name -> name.startsWith("me.wolfii.allthelogs.api."))
            .out(print)
            .err(print);
        try {
            builder.option("engine.WarnInterpreterOnly", "false");
        } catch (IllegalArgumentException ignored) {
        }
        return builder.build();
    }

    public record Result(String console, Path outputFile, String error) {
        static Result ok(String console, Path outputFile) {
            return new Result(console, outputFile, null);
        }

        static Result failed(String console, Path outputFile, String error) {
            return new Result(console, outputFile, error);
        }

        public boolean succeeded() {
            return error == null;
        }
    }
}
