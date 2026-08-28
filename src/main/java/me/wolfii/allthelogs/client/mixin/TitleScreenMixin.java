package me.wolfii.allthelogs.client.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import me.wolfii.allthelogs.client.AllTheLogsScreens;
import me.wolfii.allthelogs.client.DuckDbRuntime;
import me.wolfii.allthelogs.client.ui.screen.DuckDbSetupScreen;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the title menu until DuckDB is ready. On failure the warning screen replaces it.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Shadow
    protected abstract int getHorizontalPosition(int currentButton, int numberOfButtons, int buttonWidth);

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void allthelogs$requireDuckDb(CallbackInfo ci) {
        if (DuckDbRuntime.isReady()) return;
        ci.cancel();
        if (!DuckDbRuntime.hasFailed()) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (DuckDbRuntime.isReady()) {
                client.gui.setScreen(new TitleScreen());
            } else if (DuckDbRuntime.hasFailed() && !(client.gui.screen() instanceof DuckDbSetupScreen)) {
                client.gui.setScreen(new DuckDbSetupScreen());
            }
        });
    }

    @Definition(id = "numberOfButtons", local = @Local(type = int.class, name = "numberOfButtons"))
    @Expression("numberOfButtons = ?")
    @Inject(method = "init", at = @At(value = "MIXINEXTRAS:EXPRESSION", shift = At.Shift.AFTER))
    private void allthelogs$expandIconRow(
        CallbackInfo ci,
        @Local(name = "numberOfButtons") LocalIntRef numberOfButtons,
        @Share("addLogsButton") LocalBooleanRef addLogsButton
    ) {
        addLogsButton.set(true);
        numberOfButtons.set(numberOfButtons.get() + 1);
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I"))
    private int allthelogs$useActualIconCount(
        TitleScreen instance,
        int currentButton,
        int numberOfButtons,
        int buttonWidth,
        Operation<Integer> original,
        @Local(name = "numberOfButtons") int actualNumberOfButtons
    ) {
        return original.call(instance, currentButton, actualNumberOfButtons, buttonWidth);
    }

    @Definition(id = "width", field = "Lnet/minecraft/client/gui/screens/TitleScreen;width:I")
    @Expression("this.width / 2 - 100")
    @Inject(method = "init", at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 0))
    private void allthelogs$addLogsButton(
        CallbackInfo ci,
        @Local(name = "currentButton") LocalIntRef currentButton,
        @Local(name = "topPos") int topPos,
        @Local(name = "numberOfButtons") int numberOfButtons,
        @Share("addLogsButton") LocalBooleanRef addLogsButton
    ) {
        if (!addLogsButton.get()) return;
        currentButton.set(currentButton.get() + 1);
        Screen screen = (TitleScreen) (Object) this;
        Screens.getWidgets(screen).add(AllTheLogsScreens.logsButton(
            this.getHorizontalPosition(currentButton.get(), numberOfButtons, 20),
            topPos,
            screen));
    }
}
