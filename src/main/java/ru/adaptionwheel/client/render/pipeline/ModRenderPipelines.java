package ru.adaptionwheel.client.render.pipeline;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import ru.adaptionwheel.AdaptionWheel;

/** Client render pipelines owned by Adaption Wheel. */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ModRenderPipelines {

    private static final BindGroupLayout IMPACT_FRAME_BINDINGS = BindGroupLayout.builder()
            .withUniform("DimensionImpactUniforms", UniformType.UNIFORM_BUFFER)
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    /** Manga-blade pipeline, with the same vanilla transform, projection, fog, and globals blocks. */
    public static final RenderPipeline SLASH = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "pipeline/slash"))
            .withVertexShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "core/slash"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "core/slash"))
            // The corresponding snippets on RenderPipelines are private in 26.3. Their bind-group
            // layouts are public, so declare the exact blocks used by the shader here.
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            // 26.3 uses reversed Z (depth clears to 0); LESS_THAN_OR_EQUAL rejects every world fragment.
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    /** Full-screen triangle pipeline for the animated impact-frame pass. */
    public static final RenderPipeline DIMENSION_IMPACT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "pipeline/dimension_impact"))
            .withVertexShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "post/dimension_impact"))
            .withBindGroupLayout(IMPACT_FRAME_BINDINGS)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    private ModRenderPipelines() {
    }

    // In 26.3, EventBusSubscriber routes IModBusEvent handlers to this mod's event bus automatically.
    @SubscribeEvent
    static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SLASH);
        event.registerPipeline(DIMENSION_IMPACT);
    }
}
