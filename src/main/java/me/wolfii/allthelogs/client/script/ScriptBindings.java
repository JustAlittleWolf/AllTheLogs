package me.wolfii.allthelogs.client.script;

import me.wolfii.allthelogs.api.ChatEntry;
import me.wolfii.allthelogs.api.ChatLog;
import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.api.LogDatabase;
import me.wolfii.allthelogs.api.LogSource;
import me.wolfii.allthelogs.api.LogStoreMetadata;
import me.wolfii.allthelogs.api.MatchDay;
import me.wolfii.allthelogs.api.MatchSummary;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.util.Objects;

/**
 * Globals injected into every script: {@code allEntries}, {@code writeToOutputFile},
 * {@code database}, and the public API types.
 */
final class ScriptBindings {
    private ScriptBindings() {
    }

    static void install(Value bindings, LogDatabase database, ScriptOutput output) {
        Objects.requireNonNull(bindings, "bindings");
        JoiningLogDatabase joining = new JoiningLogDatabase(database);
        bindings.putMember("allEntries", (ProxyExecutable) args -> HostObjects.wrap(joining.allEntries()));
        bindings.putMember("writeToOutputFile", (ProxyExecutable) args -> {
            String text = args.length == 0 || args[0] == null ? "null" : String.valueOf(HostObjects.unwrap(args[0]));
            output.write(text);
            return null;
        });
        bindings.putMember("database", HostObjects.wrap(joining));
        bindings.putMember("ChatQuery", HostObjects.wrap(ChatQuery.class));
        bindings.putMember("ChatEntry", HostObjects.wrap(ChatEntry.class));
        bindings.putMember("ChatLog", HostObjects.wrap(ChatLog.class));
        bindings.putMember("LogDatabase", HostObjects.wrap(LogDatabase.class));
        bindings.putMember("LogSource", HostObjects.wrap(LogSource.class));
        bindings.putMember("LogStoreMetadata", HostObjects.wrap(LogStoreMetadata.class));
        bindings.putMember("MatchSummary", HostObjects.wrap(MatchSummary.class));
        bindings.putMember("MatchDay", HostObjects.wrap(MatchDay.class));
        bindings.putMember("Sort", HostObjects.wrap(ChatQuery.Sort.class));
    }
}
