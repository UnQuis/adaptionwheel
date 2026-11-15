package ru.adaptionwheel.client.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import ru.adaptionwheel.client.render.pipeline.ModRenderPipelines;

/** Render types for the mod's hand-built geometry. */
public final class ModRenderTypes {

    private static final RenderType SLASH = RenderType.create("adaptionwheel_slash",
            RenderSetup.builder(ModRenderPipelines.SLASH)
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup());

    private static final RenderType WHEEL_METAL = RenderType.create("adaptionwheel_wheel_metal",
            RenderSetup.builder(ModRenderPipelines.WHEEL_METAL)
                    .withTexture("Sampler0", net.minecraft.resources.Identifier.fromNamespaceAndPath(
                            ru.adaptionwheel.AdaptionWheel.MODID, "textures/entity/dharma_chakra.png"))
                    .useLightmap()
                    .useOverlay()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup());

    private ModRenderTypes() {
    }

    /**
     * The custom slash pipeline is essential: vanilla's {@code entityTranslucent} pipeline ignores
     * {@code slash.fsh}, so the coreness UVs and animated manga shading otherwise never render.
     */
    public static RenderType slash() {
        return SLASH;
    }

    /** Wheel metal render type (ported/adapted wheel_metal shader for Dharma Chakra mesh). */
    public static RenderType wheelMetal() {
        return WHEEL_METAL;
    }
}
