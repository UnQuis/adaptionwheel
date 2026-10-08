package ru.adaptionwheel.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import ru.adaptionwheel.AdaptionWheel;

/**
 * The blade's pipeline.
 *
 * <p>Built from vanilla snippets rather than from an entity snippet, because every {@code
 * ENTITY_*_SNIPPET} is an opaque pipeline: it brings the full entity vertex format, lightmap and
 * overlay samplers, depth writes, and no blending. The blade needs none of those -- it has no
 * normals, no lightmap, and it is drawn straight into a main target. So the base is
 * {@code MATRICES_FOG_SNIPPET} for {@code DynamicTransforms} and {@code Projection}, plus
 * {@code GLOBALS_SNIPPET} for {@code GameTime}, and everything else is set explicitly.
 *
 * <p>There is no {@code withBlend} or {@code withDepthWrite} on the builder in this version.
 * Blending is a property of the colour target ({@code new ColorTargetState(BlendFunction)}), and
 * depth write is the boolean on {@code DepthStencilState}.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ModRenderPipelines {

    public static final RenderPipeline SLASH = RenderPipeline.builder(
                    RenderPipelines.MATRICES_FOG_SNIPPET,
                    RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "pipeline/slash"))
            .withVertexShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "core/slash"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "core/slash"))
            // POSITION_TEX_COLOR is position, uv, colour -- which is the order slash.vsh declares
            // its attribute locations in.
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            // Depth tested against the world so the blade is occluded by terrain, but not written,
            // so it neither punches holes in itself nor occludes other translucents.
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            // Both faces: the tips collapse to near-degenerate rings, and guaranteeing a consistent
            // winding there is not worth an invisible slash if it came out backwards.
            .withCull(false)
            .build();

    private ModRenderPipelines() {
    }

    @SubscribeEvent
    static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SLASH);
    }
}