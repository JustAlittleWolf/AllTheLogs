package me.wolfii.allthelogs.client.mixin;

import me.wolfii.allthelogs.client.AllTheLogsClient;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the vanilla loading overlay up until AllTheLogs has either failed to load DuckDB or
 * opened the store and started the live session. Directory import continues on the store worker
 * after the overlay fades so a large logs folder cannot freeze the client thread or hold the
 * title screen. Live chat that arrives during that import is queued until the worker is free.
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
