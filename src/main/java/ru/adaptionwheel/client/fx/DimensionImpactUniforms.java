package ru.adaptionwheel.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import ru.adaptionwheel.client.mixin.accessor.PostPassAccessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes the dimension-impact post pass's uniform buffer and attaches it to the pass, following
 * the exact pattern impact-frames' own ShaderHelper uses for its InvertConfig block: a manually
 * sized GpuBuffer, filled via a direct ByteBuffer matching the shader's layout(std140) block
 * field-for-field (including explicit padding), written with the device's command encoder, then
 * handed to the pass through {@code PostPass.customUniforms} rather than through anything
 * declared on the RenderPipeline itself -- this map is how 26.3 replaces per-frame dynamic
 * uniform updates now that ShaderInstance.safeGetUniform(...).set(...) no longer exists.
 */
public final class DimensionImpactUniforms {

    /** vec2 + vec2 pad (16) + 4 * vec4 (64) = 96 bytes. Must track the .fsh block exactly. */
    private static final long BUFFER_SIZE = 96L;
    /** Same usage flag value impact-frames' own UBO uses (uniform + copy-dst). */
    private static final int USAGE = 136;

    private static ByteBuffer scratch;
    private static com.mojang.renderpearl.api.buffers.GpuBuffer buffer;

    private DimensionImpactUniforms() {
    }

    public static void apply(PostPass pass, float screenW, float screenH,
                             float strength, float aberration, float flash, float edgeGain,
                             float burstAge, float centreX, float centreY,
                             float ringGain, float lineGain, float crawlRate, float inkDarkness,
                             float[] ink, float[] paper) {
        if (scratch == null) {
            scratch = ByteBuffer.allocateDirect((int) BUFFER_SIZE).order(ByteOrder.nativeOrder());
        }
        if (buffer == null || buffer.isClosed()) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "ADAPTIONWHEEL DIMENSION IMPACT UBO",
                    USAGE, BUFFER_SIZE);
        }

        scratch.clear();
        scratch.putFloat(screenW).putFloat(screenH).putFloat(0f).putFloat(0f);     // ImpactSize + pad
        scratch.putFloat(strength).putFloat(aberration).putFloat(flash).putFloat(edgeGain);
        scratch.putFloat(burstAge).putFloat(0f).putFloat(centreX).putFloat(centreY);
        scratch.putFloat(ringGain).putFloat(lineGain).putFloat(crawlRate).putFloat(inkDarkness);
        scratch.putFloat(ink[0]).putFloat(ink[1]).putFloat(ink[2]).putFloat(ink[3]);
        scratch.putFloat(paper[0]).putFloat(paper[1]).putFloat(paper[2]).putFloat(paper[3]);
        scratch.flip();

        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), scratch);
        ((PostPassAccessor) (Object) pass).adaptionwheel$customUniforms()
                .put("DimensionImpactUniforms", buffer);
    }
}