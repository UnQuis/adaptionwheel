package ru.adaptionwheel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.client.ClientAdaption;

@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$steadyGaze(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && ClientAdaption.wearingWheel
                && ClientAdaption.isAdapted(Concepts.PERCEP_STEADY_GAZE)) {
            ci.cancel();
        }
    }
}
