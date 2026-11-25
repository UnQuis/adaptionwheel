package ru.adaptionwheel.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.server.TimeWarpAbility;

/** Decimates entity ticks inside an active Time Warp sphere. */
@Mixin(ServerLevel.class)
public abstract class TimeWarpServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$timeWarpTick(Entity entity, CallbackInfo ci) {
        if (TimeWarpAbility.pular(entity)) {
            ci.cancel();
        }
    }
}
