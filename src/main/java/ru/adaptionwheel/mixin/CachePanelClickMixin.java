package ru.adaptionwheel.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells vanilla that the cache extension is part of the panel.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class CachePanelClickMixin {

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$keepCacheInside(double mouseX, double mouseY, int guiLeft, int guiTop,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (ru.adaptionwheel.client.CacheOverlay.blocksOutsideClick(
                (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }
}
