package ru.adaptionwheel.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.DarknessLightmap;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {

    @Inject(method = "extract", at = @At(value = "FIELD", target =
            "Lnet/minecraft/client/renderer/state/LightmapRenderState;bossOverlayWorldDarkening:F"))
    private void adaptionwheel$liftDarkness(LightmapRenderState state, float partialTicks,
            CallbackInfo ci) {
        state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity,
                DarknessLightmap.intensity());
    }
}
