package ru.adaptionwheel.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.adaptionwheel.client.SeaEyeFog;

@Mixin(FogRenderer.class)
public abstract class FogTypeMixin {

    @Inject(method = "getFogType", at = @At("HEAD"), cancellable = true)
    private void adaptionwheel$seaEye(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (SeaEyeFog.suppresses(camera.getFluidInCamera())) {
            cir.setReturnValue(FogType.ATMOSPHERIC);
        }
    }
}