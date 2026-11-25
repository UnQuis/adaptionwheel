package ru.adaptionwheel.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.server.TimeWarpAbility;

/** Keeps client-side entity updates in step with the ticks Time Warp actually ran. */
@Mixin(ServerEntity.class)
public abstract class TimeWarpServerEntityMixin {

    @Accessor("entity")
    abstract Entity adaptionwheel$entity();

    @Accessor("updateInterval")
    abstract int adaptionwheel$updateInterval();

    @Accessor("tickCount")
    abstract int adaptionwheel$tickCount();

    @Accessor("tickCount")
    abstract void adaptionwheel$tickCount(int value);

    @Inject(method = "sendChanges", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$timeWarpSendChanges(CallbackInfo ci) {
        TimeWarpServerEntityMixin self = (TimeWarpServerEntityMixin) (Object) this;
        Entity entity = self.adaptionwheel$entity();
        int status = TimeWarpAbility.estadoEnvio(entity);
        if (status == 2 && !entity.isPassenger()) {
            ci.cancel();
        } else if (status == 1) {
            int interval = Math.max(1, self.adaptionwheel$updateInterval());
            self.adaptionwheel$tickCount((self.adaptionwheel$tickCount() / interval + 1) * interval);
        }
    }
}
