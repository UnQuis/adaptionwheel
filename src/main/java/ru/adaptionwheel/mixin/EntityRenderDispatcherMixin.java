package ru.adaptionwheel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.slashcut.CapturaCorpo;

/**
 * Marks the entity being rendered so the model-part mixin knows whose mesh to capture, and hides a
 * cut entity for the short window before the client consumes the cut packet.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "render",
            at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$beginCapture(Entity entity, double x, double y, double z,
                                            float yaw, float partialTick, PoseStack pose,
                                            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (CapturaCorpo.isHidden(entity.getId())) {
            CapturaCorpo.end();
            ci.cancel();
            return;
        }
        CapturaCorpo.begin(entity instanceof LivingEntity living ? living : null);
    }

    @Inject(method = "render",
            at = @At("TAIL"))
    private void adaptionwheel$endCapture(Entity entity, double x, double y, double z,
                                          float yaw, float partialTick, PoseStack pose,
                                          MultiBufferSource buffers, int light, CallbackInfo ci) {
        CapturaCorpo.end();
    }
}
