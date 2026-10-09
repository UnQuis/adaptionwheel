package ru.adaptionwheel.client.fx.dimension;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import ru.adaptionwheel.AdaptionWheel;

/** Resolves optional black/white frame masks without making them a hard resource dependency. */
public final class ImpactFrameTextureResolver {

    public static final int FRAME_COUNT = 5;

    private static final String[] EXTERNAL_NAMESPACES = {"impact-frames", "impact_frames"};
    private static final String TEXTURE_PATH_PREFIX = "textures/effect/frame_";

    private ImpactFrameTextureResolver() {
    }

    /**
     * Loads this mod's {@code frame_N.png} masks first, then checks compatibility namespaces.
     * Missing masks are a normal case: the built-in procedural impact panel remains available.
     */
    public static Mask resolve(Minecraft minecraft, int frame) {
        if (frame < 0 || frame >= FRAME_COUNT) {
            return null;
        }

        String path = TEXTURE_PATH_PREFIX + frame + ".png";
        Mask bundledMask = load(minecraft, Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, path));
        if (bundledMask != null) {
            return bundledMask;
        }
        for (String namespace : EXTERNAL_NAMESPACES) {
            Mask mask = load(minecraft, Identifier.fromNamespaceAndPath(namespace, path));
            if (mask != null) {
                return mask;
            }
        }
        return null;
    }

    private static Mask load(Minecraft minecraft, Identifier id) {
        if (minecraft.getResourceManager().getResource(id).isEmpty()) {
            return null;
        }

        AbstractTexture texture = minecraft.getTextureManager().getTexture(id);
        GpuTextureView view = texture.getTextureView();
        return new Mask(view,
                Math.max(1, texture.getTexture().getWidth(0)),
                Math.max(1, texture.getTexture().getHeight(0)));
    }

    public record Mask(GpuTextureView view, int width, int height) {
    }
}
