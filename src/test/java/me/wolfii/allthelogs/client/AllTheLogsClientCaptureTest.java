package me.wolfii.allthelogs.client;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link AllTheLogsClient#captureLoggedChat} is the live-session hook used by {@code ChatComponentMixin}.
 * The mixin itself is not loaded in unit tests (it targets a client HUD class). These tests cover the
 * null-safe wiring so a log-path call before the store worker exists cannot crash. Username and
 * server/world are read from {@code Minecraft} only after the worker is present.
 */
class AllTheLogsClientCaptureTest {
    @Test
    void ignoresNullAndPreInitMessages() {
        GuiMessage message = new GuiMessage(
            0,
            Component.literal("You have been poisoned"),
            null,
            GuiMessageSource.SYSTEM_SERVER,
            GuiMessageTag.system()
        );
        assertDoesNotThrow(() -> AllTheLogsClient.captureLoggedChat(null));
        assertDoesNotThrow(() -> AllTheLogsClient.captureLoggedChat(message));
    }
}
