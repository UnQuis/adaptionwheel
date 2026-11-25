package ru.adaptionwheel.mixin.client;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.adaptionwheel.client.TimeWarpClient;

/** Cheap sound approximation of the original's low-pass: quieter, deeper while warped. */
@Mixin(SoundEngine.class)
public abstract class TimeWarpSoundMixin {

    @Inject(method = "calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void adaptionwheel$warpPitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(TimeWarpClient.somTom(cir.getReturnValue()));
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"), cancellable = true)
    private void adaptionwheel$warpVolume(float volumeMultiplier, SoundSource source,
                                          CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(TimeWarpClient.somVolume(cir.getReturnValue()));
    }
}
