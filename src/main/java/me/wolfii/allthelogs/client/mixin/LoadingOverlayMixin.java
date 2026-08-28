package me.wolfii.allthelogs.client.mixin;

import me.wolfii.allthelogs.client.DuckDbRuntime;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the vanilla loading overlay up until DuckDB is ready or has failed, so a successful
 * download never shows an extra screen.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Inject(method = "isReadyToFadeOut", at = @At("RETURN"), cancellable = true)
    private void allthelogs$holdUntilDuckDb(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !DuckDbRuntime.isSettled()) {
            cir.setReturnValue(false);
        }
    }
}
