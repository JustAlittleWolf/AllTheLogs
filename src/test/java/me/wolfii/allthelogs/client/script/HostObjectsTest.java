package me.wolfii.allthelogs.client.script;

import me.wolfii.allthelogs.api.ChatQuery;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostObjectsTest {
    @Test
    void scriptsCanReadStaticConstants() {
        HostAccess access = HostAccess.newBuilder().allowPublicAccess(true).allowArrayAccess(true).build();
        try (Context context = Context.newBuilder("js").allowHostAccess(access).build()) {
            context.getBindings("js").putMember("ChatQuery", HostObjects.wrap(ChatQuery.class));
            assertEquals(ChatQuery.NULL, context.eval("js", "ChatQuery.NULL").asString());
            assertEquals(ChatQuery.NULL, context.eval("js", "ChatQuery.all().withVersion(ChatQuery.NULL).version")
                .asString());
            assertTrue(context.eval("js", "Object.keys(ChatQuery).includes('NULL')").asBoolean());
        }
    }
}
