package ru.adaptionwheel.client.fx.dimension;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.MappableRingBuffer;
import ru.adaptionwheel.AdaptionWheel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Uploads the impact shader's std140 block through a GPU-safe mapped ring buffer. */
public final class DimensionImpactUniforms {

    private static final int BUFFER_SIZE = 96;
    private static final int BUFFER_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE;

    private static MappableRingBuffer ring;

    private DimensionImpactUniforms() {
    }

    public static GpuBufferSlice upload(int screenWidth, int screenHeight,
                                        float strength, float aberration, float edgeGain,
                                        float panelProgress, float mode, float centreX, float centreY,
                                        float ringGain, float lineGain, float crawlRate, float inkDarkness,
                                        float[] ink, float[] paper) {
        if (ring == null) {
            ring = new MappableRingBuffer(
                    () -> AdaptionWheel.MODID + " dimension impact uniforms",
                    BUFFER_USAGE,
                    BUFFER_SIZE);
        }

        // Rotate before mapping so the GPU never reads memory that this frame is overwriting.
        ring.rotate();
        GpuBuffer buffer = ring.currentBuffer();
        try (var mapped = buffer.map(false, true)) {
            ByteBuffer bytes = mapped.data().order(ByteOrder.nativeOrder());
            int base = bytes.position();

            // std140: vec2 occupies a 16-byte slot, then five vec4 values follow at 16-byte
            // boundaries. Use absolute offsets; mapped views need not start at position zero.
            bytes.putFloat(base, screenWidth);
            bytes.putFloat(base + 4, screenHeight);
            bytes.putFloat(base + 8, 0f);
            bytes.putFloat(base + 12, 0f);
            putVec4(bytes, base + 16, strength, aberration, 0f, edgeGain);
            putVec4(bytes, base + 32, panelProgress, mode, centreX, centreY);
            putVec4(bytes, base + 48, ringGain, lineGain, crawlRate, inkDarkness);
            putVec4(bytes, base + 64, ink[0], ink[1], ink[2], ink[3]);
            putVec4(bytes, base + 80, paper[0], paper[1], paper[2], paper[3]);
        }

        return buffer.slice();
    }

    private static void putVec4(ByteBuffer bytes, int offset,
                                float x, float y, float z, float w) {
        bytes.putFloat(offset, x);
        bytes.putFloat(offset + 4, y);
        bytes.putFloat(offset + 8, z);
        bytes.putFloat(offset + 12, w);
    }
}
