package me.wolfii.allthelogs.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllTheLogsCommandsTest {
    @Test
    void registersGuiAndImportSubcommands() {
        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        AllTheLogsCommands.register(dispatcher);

        CommandNode<FabricClientCommandSource> root = dispatcher.getRoot().getChild("allthelogs");
        assertNotNull(root);
        assertNull(root.getCommand());
        assertNotNull(root.getChild("gui"));
        assertNotNull(root.getChild("import"));
    }
}
