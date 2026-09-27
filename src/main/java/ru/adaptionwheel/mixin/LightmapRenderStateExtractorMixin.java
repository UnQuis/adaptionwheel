package ru.adaptionwheel.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.DarknessLightmap;

/**
 * Applies the {@code Env_Darkness} lightmap lift on 26.3.
 *
 * <p>26.3 rewrote this part of the renderer: {@code LightTexture} became {@code Lightmap} plus
 * {@code LightmapRenderStateExtractor}, the lightmap is a {@code GpuTexture} rather than a
 * {@code NativeImage}, and the maths moved to {@code assets/minecraft/shaders/core/lightmap.fsh}.
 * The CPU no longer has per-pixel colours to rewrite — it fills a {@link LightmapRenderState} that
 * the shader reads, so that is what gets overridden here.</p>
 *
 * <p>Targeted at the last field the extractor assigns. It has to be after <em>every</em> one of
 * them, or the vanilla values would overwrite the lift on the same call, and it has to be inside
 * the {@code needsUpdate} branch, which is why {@code needsUpdate} itself is the wrong anchor: its
 * only access in this method is the assignment at the very top, before any value is computed.</p>
 */
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
