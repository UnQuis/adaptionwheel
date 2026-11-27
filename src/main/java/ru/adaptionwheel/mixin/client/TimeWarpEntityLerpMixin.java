package ru.adaptionwheel.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.TimeWarpClient;

/**
 * Applies a move packet to a warped entity at once instead of queuing three interpolation steps.
 *
 * <p>The client skips most ticks of a slowed entity, so vanilla's three-tick lerp would move it in
 * visible jumps. Setting {@code oldPos} to the previous packet's position and the new position
 * immediately lets the existing render interpolation spread that one step over the whole warped
 * interval.
 */
@Mixin(LivingEntity.class)
public abstract class TimeWarpEntityLerpMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$warpLerp(double x, double y, double z, float yRot, float xRot,
                                        int steps, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!TimeWarpClient.afeta(entity)) {
            return;
        }
        entity.setOldPosAndRot();
        entity.setPos(x, y, z);
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        ci.cancel();
    }
}
