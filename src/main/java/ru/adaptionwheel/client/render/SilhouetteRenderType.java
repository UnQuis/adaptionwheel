package ru.adaptionwheel.client.render;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import ru.adaptionwheel.AdaptionWheel;

/** A flat white (or black) silhouette render type, for a hit-flash: draw the model a second time
 *  with this type right after its normal pass, for 1-2 frames, same trick impact-frames uses for
 *  its invert overlay's texture override. */
public final class SilhouetteRenderType {

    public static RenderType white(Identifier texture) {
        return build("silhouette_white", texture);
    }

    public static RenderType black(Identifier texture) {
        return build("silhouette_black", texture);
    }

    private static RenderType build(String shaderName, Identifier texture) {
        return RenderType.create("adaptionwheel_" + shaderName,
                RenderSetup.builder(com.mojang.renderpearl.api.pipeline.RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                                .withLocation("adaptionwheel:misc/" + shaderName)
                                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                                .withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(BlendFunction.TRANSLUCENT))
                                .withCull(false)
                                .withFragmentShader(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID,
                                        "core/" + shaderName))
                                .build())
                        .setOutline(RenderSetup.OutlineProperty.NONE)
                        .withTexture("Sampler0", texture)
                        .createRenderSetup());
    }

    private SilhouetteRenderType() {
    }
}