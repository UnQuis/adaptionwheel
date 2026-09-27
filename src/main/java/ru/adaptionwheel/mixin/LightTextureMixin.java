package ru.adaptionwheel.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.adaptionwheel.client.DarknessLightmap;

/**
 * Applies the {@code Env_Darkness} light map lift.
 *
 * <p>One injection point, on the single {@link NativeImage#setPixelRGBA} call in
 * {@code LightTexture.updateLightTexture}. The method rebuilds all 16x16 lightmap entries once per
 * client tick ({@code LightTexture.tick} sets the dirty flag), so rewriting them here lands on the
 * very next frame and is recomputed from scratch every time — which is the whole point: there is
 * no duration to run out and no value to oscillate.</p>
 *
 * <p>Chosen over rewriting the lightmap wholesale, and over a receiver-typed redirect of
 * {@code player.hasEffect}, for two reasons. A flat overwrite throws away the day/night cycle and
 * every light level below the target; and a redirect would have to declare the receiver as exactly
 * {@code LocalPlayer} (the declared type of {@code Minecraft.player}) or Mixin rejects it at apply
 * time. {@link NativeImage} is a stable non-Minecraft type, so the handler signature is certain.</p>
 *
 * <p>Argument 2, not 0: the call is {@code setPixelRGBA(x, y, rgba)} and the colour is third.</p>
 */
@Mixin(net.minecraft.client.renderer.LightTexture.class)
public class LightTextureMixin {

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"), index = 2)
    private int adaptionwheel$liftDarkness(int argb) {
        return DarknessLightmap.lift(argb);
    }
}
