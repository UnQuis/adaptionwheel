package ru.adaptionwheel.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.TimeWarpClient;

/** Mirror the server's tick decimation on the client for entities inside the zone. */
@Mixin(ClientLevel.class)
public abstract class TimeWarpClientLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$timeWarpTick(Entity entity, CallbackInfo ci) {
        if (TimeWarpClient.pular(entity)) {
            ci.cancel();
        }
    }
}
