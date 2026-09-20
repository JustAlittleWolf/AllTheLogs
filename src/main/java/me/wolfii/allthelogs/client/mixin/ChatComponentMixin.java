package me.wolfii.allthelogs.client.mixin;

import me.wolfii.allthelogs.client.AllTheLogsClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures live session chat from {@link ChatComponent#logChatMessage}, the method that writes
 * {@code [CHAT]} lines into {@code latest.log}.
 * <p>
 * Vanilla and mods that only enqueue HUD lines via {@code addMessageToDisplayQueue} never reach
 * this logger, so debug overlays that are not file-logged stay out of the store. Player chat and
 * client/server system messages that do log are captured, including paths Fabric
 * {@code ClientReceiveMessageEvents} miss.
 * <p>
 * Not unit-tested: the mixin only forwards {@link GuiMessage#content()} into the session worker
 * on a Minecraft client class that tests do not load. Session insert and component flattening
 * are covered by existing store and {@code ComponentFormatting} tests.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Inject(method = "logChatMessage", at = @At("HEAD"))
    private void allthelogs$captureLoggedChat(GuiMessage message, CallbackInfo ci) {
        AllTheLogsClient.captureLoggedChat(message);
    }
}
