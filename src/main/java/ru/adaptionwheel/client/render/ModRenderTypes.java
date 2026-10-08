package ru.adaptionwheel.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import ru.adaptionwheel.AdaptionWheel;

/** Render types for the mod's own hand-built geometry. */
public final class ModRenderTypes {

    /**
     * The slash mesh samples no texture -- its colour is baked per vertex by {@link
     * SlashBladeMesh} -- so this reuses the same 1x1 white surface trick {@code
     * FlyingSlashRenderer} uses for its own translucent quads: {@code entityTranslucent} needs a
     * bound texture, and this one is never actually read.
     */
    private static final Identifier SURFACE =
            Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/white.png");

    private ModRenderTypes() {
    }

    public static RenderType slash() {
        return RenderTypes.entityTranslucent(SURFACE);
    }
}