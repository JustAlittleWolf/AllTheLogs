package me.wolfii.allthelogs.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.wolfii.allthelogs.client.AllTheLogsScreens;
import me.wolfii.allthelogs.client.DuckDbRuntime;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the AllTheLogs icon to the pause-menu icon row, the same way ScreenshotViewer does.
 */
@Mixin(PauseScreen.class)
public class PauseScreenMixin {
    @Inject(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSingleplayerServer()Lnet/minecraft/client/server/IntegratedServer;"))
    private void allthelogs$addLogsButton(CallbackInfo ci, @Local(name = "iconButtonRow") LinearLayout iconButtonRow) {
        if (!DuckDbRuntime.isReady()) return;
        iconButtonRow.addChild(AllTheLogsScreens.logsButton(0, 0, (Screen) (Object) this));
    }
}
