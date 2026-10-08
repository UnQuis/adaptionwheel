package ru.adaptionwheel.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import ru.adaptionwheel.client.mixin.accessor.PostPassAccessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Per-frame std140 uniform upload for the Dimension Destroy post pass. The block layout is shared
 * with {@code shaders/post/dimension_impact.fsh}: a padded vec2 followed by five vec4 values.
 */
public final class DimensionImpactUniforms {

    /** vec2 + std140 padding (16 bytes), followed by five vec4s (80 bytes). */
    private static final long BUFFER_SIZE = 96L;
    /** UNIFORM | COPY_DST, matching the post pass's dynamic uniform buffer usage. */
    private static final int USAGE = 136;

    private static ByteBuffer scratch;
    private static GpuBuffer buffer;

    private DimensionImpactUniforms() {
    }

    public static void apply(PostPass pass, float screenW, float screenH,
                             float strength, float aberration, float flash, float edgeGain,
                             float panelProgress, float mode, float centreX, float centreY,
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
        scratch.putFloat(screenW).putFloat(screenH).putFloat(0f).putFloat(0f); // ImpactSize + std140 pad
        scratch.putFloat(strength).putFloat(aberration).putFloat(flash).putFloat(edgeGain);
        scratch.putFloat(panelProgress).putFloat(mode).putFloat(centreX).putFloat(centreY);
        scratch.putFloat(ringGain).putFloat(lineGain).putFloat(crawlRate).putFloat(inkDarkness);
        scratch.putFloat(ink[0]).putFloat(ink[1]).putFloat(ink[2]).putFloat(ink[3]);
        scratch.putFloat(paper[0]).putFloat(paper[1]).putFloat(paper[2]).putFloat(paper[3]);
        scratch.flip();

        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), scratch);
        ((PostPassAccessor) (Object) pass).adaptionwheel$customUniforms()
                .put("DimensionImpactUniforms", buffer);
    }
}
