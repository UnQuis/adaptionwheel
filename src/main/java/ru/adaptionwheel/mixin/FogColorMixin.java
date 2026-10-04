package ru.adaptionwheel.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.adaptionwheel.client.SeaEyeFog;

@Mixin(FogRenderer.class)
public abstract class FogColorMixin {

    @Redirect(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private FogType adaptionwheel$seaEyeColor(Camera camera) {
        FogType reported = camera.getFluidInCamera();
        return SeaEyeFog.suppresses(reported) ? FogType.NONE : reported;
    }
}