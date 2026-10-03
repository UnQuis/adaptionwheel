package ru.adaptionwheel.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.adaptionwheel.client.DarknessLightmap;

@Mixin(net.minecraft.client.renderer.LightTexture.class)
public class LightTextureMixin {

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"), index = 2)
    private int adaptionwheel$liftDarkness(int argb) {
        return DarknessLightmap.lift(argb);
    }
}
