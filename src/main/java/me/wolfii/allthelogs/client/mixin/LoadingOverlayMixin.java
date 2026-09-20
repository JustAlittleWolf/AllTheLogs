package me.wolfii.allthelogs.client.mixin;

import me.wolfii.allthelogs.client.AllTheLogsClient;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the vanilla loading overlay up until AllTheLogs has either failed to load DuckDB or
 * finished opening the store, importing (including post-import clustering/compact), and starting
 * the live session. A successful DuckDB download therefore never shows an extra screen, and the
 * store worker is free once the title screen appears.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Inject(method = "isReadyToFadeOut", at = @At("RETURN"), cancellable = true)
    private void allthelogs$holdUntilStoreBoot(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !AllTheLogsClient.isBootSettled()) {
            cir.setReturnValue(false);
        }
    }
}
