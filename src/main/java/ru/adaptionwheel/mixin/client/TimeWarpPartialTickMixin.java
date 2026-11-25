package ru.adaptionwheel.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.TimeWarpClient;

/** Substitute partialTick for one entity, then call the original renderer once. */
@Mixin(LevelRenderer.class)
public abstract class TimeWarpPartialTickMixin {

    private boolean adaptionwheel$rerendering;

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$warpPartial(Entity entity, double camX, double camY, double camZ,
                                           float partialTick, PoseStack poseStack,
                                           MultiBufferSource bufferSource, CallbackInfo ci) {
        if (adaptionwheel$rerendering) {
            return;
        }
        float warped = TimeWarpClient.parcialPara(entity, partialTick);
        if (warped == partialTick) {
            return;
        }
        ci.cancel();
        adaptionwheel$rerendering = true;
        try {
            adaptionwheel$renderEntity(entity, camX, camY, camZ, warped, poseStack, bufferSource);
        } finally {
            adaptionwheel$rerendering = false;
        }
    }

    @Invoker("renderEntity")
    private void adaptionwheel$renderEntity(Entity entity, double camX, double camY, double camZ,
                                            float partialTick, PoseStack poseStack,
                                            MultiBufferSource bufferSource) {
        throw new AssertionError();
    }
}
