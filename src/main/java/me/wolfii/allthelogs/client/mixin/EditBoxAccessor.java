package me.wolfii.allthelogs.client.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the search field slide its editable text past the regex chrome. {@code textX} is recomputed whenever
 * the box moves, so the chrome inset has to be applied again afterwards.
 */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("textX")
    int allthelogs$getTextX();

    @Accessor("textX")
    void allthelogs$setTextX(int textX);
}
