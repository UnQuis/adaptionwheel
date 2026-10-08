package ru.adaptionwheel.client.fx.dimension;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.render.pipeline.ModRenderPipelines;

/** Runs the impact shader in explicit 26.3 render passes, outside the world's frame graph. */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DimensionImpactRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SCRATCH_LABEL = "Adaption Wheel impact-frame scratch";

    private static TextureTarget scratchTarget;
    private static int scratchWidth = -1;
    private static int scratchHeight = -1;
    private static boolean pipelineWarningLogged;
    private static boolean renderWarningLogged;

    private DimensionImpactRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (!DimensionImpactFX.active()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0 || !ensureScratch(width, height)) {
            return;
        }

        GpuTextureView mainColor = mainTarget.getColorTextureView();
        GpuTextureView scratchColor = scratchTarget.getColorTextureView();
        if (mainColor == null || scratchColor == null) {
            return;
        }

        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(ModRenderPipelines.DIMENSION_IMPACT);
        if (pipeline == null || pipeline.isClosed()) {
            if (!pipelineWarningLogged) {
                pipelineWarningLogged = true;
                LOGGER.error("The Dimension Destroy impact pipeline is not compiled; the GUI flash remains available.");
            }
            return;
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

            // Never sample from the target currently being written. First copy main into a
            // same-size scratch texture, then read scratch while writing the transformed result
            // back to main.
            GpuBufferSlice copyUniforms = DimensionImpactFX.writeUniforms(width, height, true);
            drawPass(encoder, pipeline, scratchColor, mainColor, copyUniforms, nearest,
                    "Adaption Wheel impact-frame copy");

            GpuBufferSlice panelUniforms = DimensionImpactFX.writeUniforms(width, height, false);
            drawPass(encoder, pipeline, mainColor, scratchColor, panelUniforms, nearest,
                    "Adaption Wheel impact-frame panel");
        } catch (RuntimeException exception) {
            if (!renderWarningLogged) {
                renderWarningLogged = true;
                LOGGER.error("Could not draw the Dimension Destroy impact frame", exception);
            }
        }
    }

    private static boolean ensureScratch(int width, int height) {
        if (scratchTarget != null && scratchWidth == width && scratchHeight == height) {
            return true;
        }

        if (scratchTarget != null) {
            scratchTarget.destroyBuffers();
            scratchTarget = null;
        }

        try {
            scratchTarget = new TextureTarget(SCRATCH_LABEL, width, height, GpuFormat.RGBA8_UNORM, null);
            scratchWidth = width;
            scratchHeight = height;
            return true;
        } catch (RuntimeException exception) {
            if (!renderWarningLogged) {
                renderWarningLogged = true;
                LOGGER.error("Could not allocate the Dimension Destroy impact-frame scratch target", exception);
            }
            return false;
        }
    }

    private static void drawPass(CommandEncoder encoder, CompiledRenderPipeline pipeline,
                                 GpuTextureView output, GpuTextureView input,
                                 GpuBufferSlice uniforms, GpuSampler sampler,
                                 String label) {
        RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> label)
                .withColorAttachment(output)
                .build();

        try (RenderPass pass = encoder.createRenderPass(descriptor)) {
            pass.setPipeline(pipeline);
            pass.setUniform("DimensionImpactUniforms", uniforms);
            pass.setUniform("InSampler", input, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }
}
