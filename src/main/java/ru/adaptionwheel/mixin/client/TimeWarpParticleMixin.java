package ru.adaptionwheel.mixin.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.TimeWarpClient;

/** Skip the same client ticks for particles that entities skip inside a warp zone. */
@Mixin(ParticleEngine.class)
public abstract class TimeWarpParticleMixin {

    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$timeWarpParticle(Particle particle, CallbackInfo ci) {
        if (TimeWarpClient.pularParticula(particle.getPos())) {
            ci.cancel();
        }
    }
}
