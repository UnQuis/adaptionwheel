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

    private ModRenderTypes() {
    }

    /**
     * The custom slash pipeline is essential: vanilla's {@code entityTranslucent} pipeline ignores
     * {@code slash.fsh}, so the coreness UVs and animated manga shading otherwise never render.
     */
    public static RenderType slash() {
        return SLASH;
    }
}
